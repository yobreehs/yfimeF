package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.shared.settings.preference.NoTitlePreferenceCategory
import app.revanced.extension.youtube.patches.playback.quality.RememberVideoQualityPatch
import com.google.android.libraries.youtube.innertube.model.media.VideoQuality
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.getIntField
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.ListPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceCategory
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import org.luckypray.dexkit.wrap.DexClass
import java.lang.reflect.Modifier

private lateinit var getQualityName: (VideoQuality) -> String
private lateinit var getResolution: (VideoQuality) -> Int

fun VideoQuality.getResolution() = getResolution(this)
fun VideoQuality.getQualityName() = getQualityName(this)

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

    playerInitHooks.add { controller ->
        RememberVideoQualityPatch.newVideoStarted(controller)
    }

    val YOUTUBE_VIDEO_QUALITY_CLASS_TYPE =
        "Lcom/google/android/libraries/youtube/innertube/model/media/VideoQuality;"

    val videoQualityClass = DexClass(YOUTUBE_VIDEO_QUALITY_CLASS_TYPE).toClass()
    val qualityNameField = videoQualityClass.findFirstFieldByExactType(String::class.java)
    val resolutionField = videoQualityClass.findFirstFieldByExactType(Int::class.java)

    getQualityName = { quality -> qualityNameField.get(quality) as String }
    getResolution = { quality -> resolutionField.get(quality) as Int }

    // Fix bad data used by YouTube.
    XposedBridge.hookAllConstructors(
        videoQualityClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val quality = param.thisObject as VideoQuality
                val newResolution = RememberVideoQualityPatch.fixVideoQualityResolution(
                    quality.getQualityName(), quality.getResolution()
                )
                resolutionField.set(quality, newResolution)
            }
        })

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

    getDexMethod("setVideoQualityFingerprint") {
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
            getDexField("onItemClickListenerClassReference") {
                usingFields[0].field
            }
            getDexField("setQualityFieldReference") {
                usingFields[1].field
            }
            getDexMethod("setQualityMenuIndexMethod") {
                usingFields[1].field.type.findMethod {
                    matcher { addParamType { descriptor = YOUTUBE_VIDEO_QUALITY_CLASS_TYPE } }
                }.single()
            }
        }
    }

    // Inject a call to set the remembered quality once a video loads.
    videoQualitySetterFingerprint.hookMethod(object : XC_MethodHook() {
        val onItemClickListenerClass = getDexField("onItemClickListenerClassReference").toField()
        val setQualityField = getDexField("setQualityFieldReference").toField()
        val setQualityMenuIndexMethod = getDexMethod("setQualityMenuIndexMethod").toMethod()

        @Suppress("UNCHECKED_CAST")
        override fun beforeHookedMethod(param: MethodHookParam) {
            val qualities = param.args[0] as Array<out VideoQuality>
            val originalQualityIndex = param.args[1] as Int
            val menu = param.thisObject.let { onItemClickListenerClass.get(it) }
                .let { setQualityField.get(it) }

            param.args[1] = RememberVideoQualityPatch.setVideoQuality(
                qualities,
                { quality -> setQualityMenuIndexMethod(menu, quality) },
                originalQualityIndex
            )
        }
    })

    // Inject a call to remember the selected quality for Shorts.
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

    // Inject a call to remember the user selected quality for regular videos.
    getDexMethod("videoQualityChangedFingerprint") {
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
            RememberVideoQualityPatch.userChangedQuality(selectedQualityIndex)
        }
    })
}