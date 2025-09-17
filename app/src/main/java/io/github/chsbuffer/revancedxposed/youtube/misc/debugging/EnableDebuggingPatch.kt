package io.github.chsbuffer.revancedxposed.youtube.misc.debugging

import app.revanced.extension.shared.settings.preference.ClearLogBufferPreference
import app.revanced.extension.shared.settings.preference.ExportLogToClipboardPreference
import app.revanced.extension.youtube.patches.EnableDebuggingPatch
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.NonInteractivePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen

fun YoutubeHook.EnableDebugging() {
    PreferenceScreen.MISC.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_debug_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("revanced_debug"),
                SwitchPreference("revanced_debug_protobuffer"),
                SwitchPreference("revanced_debug_stacktrace"),
                SwitchPreference("revanced_debug_toast_on_error"),
                NonInteractivePreference(
                    "revanced_debug_export_logs_to_clipboard",
                    tag = ExportLogToClipboardPreference::class.java,
                    selectable = true,
                ),
                NonInteractivePreference(
                    "revanced_debug_logs_clear_buffer",
                    tag = ClearLogBufferPreference::class.java,
                    selectable = true,
                ),
            ),
        ),
    )

    // Hook the methods that look up if a feature flag is active.
    ::experimentalBooleanFeatureFlagFingerprint.hookMethod {
        after {
            it.result = EnableDebuggingPatch.isBooleanFeatureFlagEnabled(
                it.result as Boolean,
                it.args[1] as Long
            )
        }
    }

    ::experimentalDoubleFeatureFlagFingerprint.hookMethod {
        after {
            it.result = EnableDebuggingPatch.isDoubleFeatureFlagEnabled(
                it.result as Double,
                it.args[0] as Long,
                it.args[1] as Double
            )
        }
    }

    ::experimentalLongFeatureFlagFingerprint.hookMethod {
        after {
            it.result = EnableDebuggingPatch.isLongFeatureFlagEnabled(
                it.result as Long,
                it.args[0] as Long,
                it.args[1] as Long
            )
        }
    }

    ::experimentalStringFeatureFlagFingerprint.hookMethod {
        after {
            it.result = EnableDebuggingPatch.isStringFeatureFlagEnabled(
                it.result as String,
                it.args[0] as Long,
                it.args[1] as String
            )
        }
    }

    // There exists other experimental accessor methods for byte[]
    // and wrappers for obfuscated classes, but currently none of those are hooked.
}
