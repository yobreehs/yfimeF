package io.github.chsbuffer.revancedxposed.youtube.misc.settings

import android.app.Activity
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.shared.settings.preference.ReVancedAboutPreference
import app.revanced.extension.youtube.settings.LicenseActivityHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.invokeOriginalMethod
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreferenceScreen
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.NonInteractivePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.settings.preferences
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

@Suppress("UNREACHABLE_CODE")
fun YoutubeHook.SettingsHook() {
    ::PreferenceFragmentCompat_addPreferencesFromResource.hookMethod(scopedHook(::PreferenceInflater_inflate.member) {
        before { param ->
            val context = Utils.getContext()
            val preferencesName = context.resources.getResourceName(outerParam.args[0] as Int)
            Logger.printDebug { "addPreferencesFromResource $preferencesName" }
            if (!preferencesName.contains("settings_fragment")) return@before
            val xml =
                if (preferencesName.contains("settings_fragment_cairo")) R.xml.yt_revanced_settings_cairo else R.xml.yt_revanced_settings
            XposedBridge.invokeOriginalMethod(
                param.method, param.thisObject, param.args.clone().apply {
                    this[0] = context.resources.getXml(xml)
                })
        }
    })

    ::licenseActivityOnCreateFingerprint.hookMethod(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as Activity
            val hook = LicenseActivityHook.createInstance()
            // must set theme before original set theme
            hook.customizeActivityTheme(activity)

            try {
                param.invokeOriginalMethod()
            } catch (e: Throwable) {
                // ignored
            }

            LicenseActivityHook.initialize(hook, activity)
        }
    })

    // Remove other methods as they will break as the onCreate method is modified above.
    ::licenseActivityNOTonCreate.dexMethodList.forEach {
        if (it.returnTypeName == "void") it.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    // Update shared dark mode status based on YT theme.
    // This is needed because YT allows forcing light/dark mode
    // which then differs from the system dark mode status.
    ::setThemeFingerprint.hookMethod {
        after { param ->
            LicenseActivityHook.updateLightDarkModeStatus(param.result as Enum<*>)
        }
    }
    preferences += NonInteractivePreference(
        key = "revanced_settings_screen_00_about",
        icon = "@drawable/revanced_settings_screen_00_about",
        layout = "@layout/preference_with_icon",
        summaryKey = null,
        tag = ReVancedAboutPreference::class.java,
        selectable = true,
    )
    PreferenceScreen.close()
}

object PreferenceScreen : BasePreferenceScreen() {
    // Sort screens in the root menu by key, to not scatter related items apart
    // (sorting key is set in revanced_prefs.xml).
    // If no preferences are added to a screen, the screen will not be added to the settings.
    val ADS = Screen(
        key = "revanced_settings_screen_01_ads",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_01_ads",
        layout = "@layout/preference_with_icon",
    )
    val ALTERNATIVE_THUMBNAILS = Screen(
        key = "revanced_settings_screen_02_alt_thumbnails",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_02_alt_thumbnails",
        layout = "@layout/preference_with_icon",
        sorting = PreferenceScreenPreference.Sorting.UNSORTED,
    )
    val FEED = Screen(
        key = "revanced_settings_screen_03_feed",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_03_feed",
        layout = "@layout/preference_with_icon",
    )
    val GENERAL_LAYOUT = Screen(
        key = "revanced_settings_screen_04_general",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_04_general",
        layout = "@layout/preference_with_icon",
    )
    val PLAYER = Screen(
        key = "revanced_settings_screen_05_player",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_05_player",
        layout = "@layout/preference_with_icon",
    )

    val SHORTS = Screen(
        key = "revanced_settings_screen_06_shorts",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_06_shorts",
        layout = "@layout/preference_with_icon",
    )

    val SEEKBAR = Screen(
        key = "revanced_settings_screen_07_seekbar",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_07_seekbar",
        layout = "@layout/preference_with_icon",
    )
    val SWIPE_CONTROLS = Screen(
        key = "revanced_settings_screen_08_swipe_controls",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_08_swipe_controls",
        layout = "@layout/preference_with_icon",
        sorting = PreferenceScreenPreference.Sorting.UNSORTED,
    )
    val RETURN_YOUTUBE_DISLIKE = Screen(
        key = "revanced_settings_screen_09_return_youtube_dislike",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_09_return_youtube_dislike",
        layout = "@layout/preference_with_icon",
        sorting = PreferenceScreenPreference.Sorting.UNSORTED,
    )
    val SPONSORBLOCK = Screen(
        key = "revanced_settings_screen_10_sponsorblock",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_10_sponsorblock",
        layout = "@layout/preference_with_icon",
        sorting = PreferenceScreenPreference.Sorting.UNSORTED,
    )
    val MISC = Screen(
        key = "revanced_settings_screen_11_misc",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_11_misc",
        layout = "@layout/preference_with_icon",
    )
    val VIDEO = Screen(
        key = "revanced_settings_screen_12_video",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_12_video",
        layout = "@layout/preference_with_icon",
        sorting = PreferenceScreenPreference.Sorting.BY_KEY,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}