package io.github.chsbuffer.revancedxposed.youtube.video.audio

import app.revanced.extension.youtube.patches.ForceOriginalAudioPatch
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.debugging.experimentalBooleanFeatureFlagFingerprint
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.shared.mainActivityOnCreateFingerprint

fun YoutubeHook.ForceOriginalAudio() {
    PreferenceScreen.VIDEO.addPreferences(
        SwitchPreference("revanced_force_original_audio")
    )

    ::mainActivityOnCreateFingerprint.hookMethod {
        before {
            ForceOriginalAudioPatch.setPreferredLanguage()
        }
    }

    // Disable feature flag that ignores the default track flag
    // and instead overrides to the user region language.
    ::experimentalBooleanFeatureFlagFingerprint.hookMethod {
        after {
            if (it.args[1] == AUDIO_STREAM_IGNORE_DEFAULT_FEATURE_FLAG) {
                it.result =
                    ForceOriginalAudioPatch.ignoreDefaultAudioStream(it.result as Boolean)
            }
        }
    }

    ::getIsDefaultAudioTrackFingerprint.hookMethod {
        val getAudioTrackIdMethod = ::getAudioTrackIdFingerprint.method
        val getAudioTrackDisplayNameMethod = ::getAudioTrackDisplayNameFingerprint.method
        after {
            it.result = ForceOriginalAudioPatch.isDefaultAudioStream(
                it.result as Boolean,
                getAudioTrackIdMethod(it.thisObject) as String?,
                getAudioTrackDisplayNameMethod(it.thisObject) as String?
            )
        }
    }
}
