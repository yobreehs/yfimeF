package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.shared.settings.preference.NoTitlePreferenceCategory
import app.revanced.extension.youtube.patches.playback.quality.RememberVideoQualityPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.AccessFlags
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

    playerInitHooks.add { controller ->
        RememberVideoQualityPatch.newVideoStarted(controller)
    }

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