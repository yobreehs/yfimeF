package io.github.chsbuffer.revancedxposed.youtube.misc.backgroundplayback

import app.revanced.extension.youtube.patches.BackgroundPlaybackPatch
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen

fun YoutubeHook.BackgroundPlayback() {

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("revanced_shorts_disable_background_playback"),
    )

    ::backgroundPlaybackManagerFingerprint.hookMethod {
        after {
            it.result = BackgroundPlaybackPatch.isBackgroundPlaybackAllowed(it.result as Boolean)
        }
    }
    ::backgroundPlaybackManagerShortsFingerprint.hookMethod {
        after {
            it.result =
                BackgroundPlaybackPatch.isBackgroundShortsPlaybackAllowed(it.result as Boolean)
        }
    }

    // Enable background playback option in YouTube settings
    ::backgroundPlaybackSettingsSubFingerprint.hookMethod(returnConstant(true))

    // Force allowing background play for Shorts.
    ::shortsBackgroundPlaybackFeatureFlagFingerprint.hookMethod(returnConstant(true))

    // Force allowing background play for videos labeled for kids.
    ::kidsBackgroundPlaybackPolicyControllerFingerprint.hookMethod(returnConstant(Unit))

    // TODO Fix PiP buttons not working after locking/unlocking device screen.
}