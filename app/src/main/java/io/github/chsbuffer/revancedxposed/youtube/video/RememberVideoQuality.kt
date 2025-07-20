package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.shared.settings.preference.NoTitlePreferenceCategory
import app.revanced.extension.youtube.patches.playback.quality.RememberVideoQualityPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.getIntField
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.ListPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceCategory
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import java.lang.reflect.Modifier

fun YoutubeHook.RememberVideoQuality() {
    val settingsMenuVideoQualityGroup = setOf(
        ListPreference(
            key = "revanced_video_quality_default_mobile",
            entriesKey = "revanced_video_quality_default_entries",
            entryValuesKey = "revanced_video_quality_default_entry_values"
        ),
        ListPreference(
            key = "revanced_video_quality_default_wifi",
            entriesKey = "revanced_video_quality_default_entries",
            entryValuesKey = "revanced_video_quality_default_entry_values"
        ),
        SwitchPreference("revanced_remember_video_quality_last_selected"),

        ListPreference(
            key = "revanced_shorts_quality_default_mobile",
            entriesKey = "revanced_shorts_quality_default_entries",
            entryValuesKey = "revanced_shorts_quality_default_entry_values",
        ),
        ListPreference(
            key = "revanced_shorts_quality_default_wifi",
            entriesKey = "revanced_shorts_quality_default_entries",
            entryValuesKey = "revanced_shorts_quality_default_entry_values"
        ),
        SwitchPreference("revanced_remember_shorts_quality_last_selected"),
        SwitchPreference("revanced_remember_video_quality_last_selected_toast")
    )

    PreferenceScreen.VIDEO.addPreferences(
        // Keep the preferences organized together.
        PreferenceCategory(
            key = "revanced_01_video_key", // Dummy key to force the quality preferences first.
            titleKey = null,
            sorting = Sorting.UNSORTED,
            tag = NoTitlePreferenceCategory::class.java,
            preferences = settingsMenuVideoQualityGroup
        )
    )

    /*
     * The following code works by hooking the method which is called when the user selects a video quality
     * to remember the last selected video quality.
     *
     * It also hooks the method which is called when the video quality to set is determined.
     * Conveniently, at this point the video quality is overridden to the remembered playback speed.
     */
    playerInitHooks.add { controller ->
        RememberVideoQualityPatch.newVideoStarted(controller)
    }

    val videoQualitySetterFingerprint = getDexMethod("videoQualitySetterFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("V")
            parameters("[L", "I", "Z")
            opcodes(
                Opcode.IF_EQZ,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.IPUT_BOOLEAN,
            )
            strings("menu_item_video_quality")
        }
    }

    getDexMethod("setQualityByIndexMethodClassFieldReferenceFingerprint") {
        fingerprint {
            returns("V")
            parameters("L")
            opcodes(
                Opcode.IGET_OBJECT,
                Opcode.IPUT_OBJECT,
                Opcode.IGET_OBJECT,
            )
            classMatcher {
                className = videoQualitySetterFingerprint.className
            }
        }.also { method ->
            val usingFields = method.usingFields
            getDexField("getOnItemClickListenerClassReference") {
                usingFields[0].field
            }
            getDexField("getSetQualityByIndexMethodClassFieldReference") {
                usingFields[1].field
            }
            getDexMethod("setQualityByIndexMethod") {
                usingFields[1].field.type.findMethod { matcher { paramTypes("int") } }.single()
            }
        }
    }

    // Inject a call to set the remembered quality once a video loads.
    videoQualitySetterFingerprint.hookMethod(object : XC_MethodHook() {
        val getOnItemClickListener = getDexField("getOnItemClickListenerClassReference").toField()
        val getSetQualityByIndexMethod =
            getDexField("getSetQualityByIndexMethodClassFieldReference").toField()
        val setQualityByIndexMethod = getDexMethod("setQualityByIndexMethod").name

        @Suppress("UNCHECKED_CAST")
        override fun beforeHookedMethod(param: MethodHookParam) {
            val qualities = param.args[0] as Array<out Any>
            val originalQualityIndex = param.args[1] as Int
            val qInterface = param.thisObject.let { getOnItemClickListener.get(it) }
                .let { getSetQualityByIndexMethod.get(it) }
            val qIndexMethod = setQualityByIndexMethod
            param.args[1] = RememberVideoQualityPatch.setVideoQuality(
                qualities, originalQualityIndex, qInterface, qIndexMethod
            )
        }
    })


    // Inject a call to remember the selected quality.
    getDexMethod("videoQualityItemOnClickParentFingerprint") {
        fingerprint {
            returns("V")
            strings("VIDEO_QUALITIES_MENU_BOTTOM_SHEET_FRAGMENT")
        }.declaredClass!!.findMethod {
            matcher {
                name = "onItemClick"
            }
        }.single()
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            RememberVideoQualityPatch.userChangedQuality(param.args[2] as Int)
        }
    })

    // Remember video quality if not using old layout menu.
    getDexMethod("newVideoQualityChangedFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            methodMatcher {
                addInvoke {
                    declaredClass =
                        "com.google.android.libraries.youtube.innertube.model.media.VideoQuality"
                    name = "<init>"
                }
                addUsingField {
                    field {
                        // VIDEO_QUALITY_SETTING_UNKNOWN Enum
                        declaredClass { usingStrings("VIDEO_QUALITY_SETTING_UNKNOWN") }
                        modifiers = Modifier.STATIC
                        name = "a"
                    }
                }
            }
        }.also { method ->
            getDexMethod("VideoQualityReceiver") {
                method.invokes.single { it.paramCount == 1 && it.paramTypeNames[0] == "com.google.android.libraries.youtube.innertube.model.media.VideoQuality" }
            }
        }
    }.hookMethod(ScopedHook(getDexMethod("VideoQualityReceiver").toMember()) {
        before {
            val selectedQualityIndex = param.args[0].getIntField("a")
            RememberVideoQualityPatch.userChangedQualityInNewFlyout(selectedQualityIndex)
        }
    })
}