package io.github.chsbuffer.revancedxposed.music.misc.settings

import android.app.Activity
import app.revanced.extension.music.settings.GoogleApiActivityHook
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.shared.settings.preference.ClearLogBufferPreference
import app.revanced.extension.shared.settings.preference.ExportLogToClipboardPreference
import app.revanced.extension.shared.settings.preference.ReVancedAboutPreference
import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.invokeOriginalMethod
import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreferenceScreen
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.NonInteractivePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.shared.settings.preferences
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceFragmentCompat_addPreferencesFromResource

fun MusicHook.SettingsHook() {
    val addPreferencesFromResource = ::PreferenceFragmentCompat_addPreferencesFromResource.method

    ::PreferenceFragmentCompat_setPreferencesFromResource.hookMethod {
        before {
            val context = Utils.getContext()
            val preferencesName = context.resources.getResourceName(it.args[0] as Int)
            Logger.printDebug { "setPreferencesFromResource $preferencesName" }
            if (!preferencesName.endsWith("settings_headers")) return@before
            addPreferencesFromResource(it.thisObject, R.xml.yt_revanced_settings_music)
            addPreferencesFromResource(it.thisObject, it.args[0])
            it.result = Unit
        }
    }

    // Should make a separate debugging patch, but for now include it with all installations.
    PreferenceScreen.MISC.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_debug_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("revanced_debug"),
                NonInteractivePreference(
                    "revanced_debug_export_logs_to_clipboard",
                    tag = ExportLogToClipboardPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    "revanced_debug_logs_clear_buffer",
                    tag = ClearLogBufferPreference::class.java,
                    selectable = true
                )
            )
        )
    )

    // Add an "About" preference to the top.
    preferences += NonInteractivePreference(
        key = "revanced_settings_music_screen_0_about",
        summaryKey = null,
        tag = ReVancedAboutPreference::class.java,
        selectable = true,
    )

    ::googleApiActivityFingerprint.hookMethod {
        before { param ->
            param.invokeOriginalMethod()
            val activity = param.thisObject as Activity
            val hook = GoogleApiActivityHook.createInstance()
            GoogleApiActivityHook.initialize(hook, activity)
            val musicTheme = Utils.getResourceIdentifier("@style/Theme.YouTubeMusic", "style")
            activity.setTheme(musicTheme)
            param.result = Unit
        }
    }

    // Remove other methods as they will break as the onCreate method is modified above.
    ::googleApiActivityNOTonCreate.dexMethodList.forEach {
        if (it.returnTypeName == "void") it.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    PreferenceScreen.close()
}

object PreferenceScreen : BasePreferenceScreen() {
    val ADS = Screen(
        "revanced_settings_music_screen_1_ads", summaryKey = null
    )
    val GENERAL = Screen(
        "revanced_settings_music_screen_2_general", summaryKey = null
    )
    val PLAYER = Screen(
        "revanced_settings_music_screen_3_player", summaryKey = null
    )
    val MISC = Screen(
        "revanced_settings_music_screen_4_misc", summaryKey = null
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}
