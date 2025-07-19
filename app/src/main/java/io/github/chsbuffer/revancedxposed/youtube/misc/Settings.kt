package io.github.chsbuffer.revancedxposed.youtube.misc

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Resources
import android.webkit.WebView
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.shared.settings.preference.ReVancedAboutPreference
import app.revanced.extension.youtube.settings.LicenseActivityHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.addModuleAssets
import io.github.chsbuffer.revancedxposed.invokeOriginalMethod
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreferenceScreen
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.IntentPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.NonInteractivePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier
import kotlin.system.exitProcess

val preferences = mutableSetOf<BasePreference>()

fun addSettingPreference(screen: BasePreference) {
    preferences += screen
}

fun newIntent(settingsName: String) = IntentPreference.Intent(
    data = settingsName,
    targetClass = "com.google.android.libraries.social.licenses.LicenseActivity",
) { "com.google.android.youtube" }

@Suppress("UNREACHABLE_CODE")
fun YoutubeHook.SettingsHook() {
    val inflate = getDexMethod("PreferenceInflater#inflate") {
        findMethod {
            matcher {
                returnType = "androidx.preference.Preference"
                paramTypes(
                    "org.xmlpull.v1.XmlPullParser",
                    "androidx.preference.PreferenceGroup",
                    "android.content.Context",
                    "java.lang.Object[]",
                    null,
                    "java.lang.String[]"
                )
                usingEqStrings(": No start tag found!", ": ")
            }
        }.single()
    }

    getDexMethod("PreferenceFragmentCompat#addPreferencesFromResource") {
        findClass {
            matcher {
                usingStrings(
                    "Could not create RecyclerView",
                    "Content has view with id attribute 'android.R.id.list_container' that is not a ViewGroup class",
                    "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"
                )
            }
        }.single().let { preferenceFragmentCompat ->
            preferenceFragmentCompat.findMethod {
                matcher {
                    returnType = "void"
                    paramTypes("int")
                }
            }.singleOrNull() ?: preferenceFragmentCompat.findMethod {
                matcher {
                    name = "addPreferencesFromResource"
                }
            }.single()
        }
    }.hookMethod(
        ScopedHook(inflate.toMethod()) {
            var handleWebView = false
            after {
                val preferencesName = app.resources.getResourceName(outerParam.args[0] as Int)
                Logger.printDebug { "addPreferencesFromResource $preferencesName" }
                if (!preferencesName.contains("settings_fragment")) return@after
                if (!handleWebView) {
                    // workaround "AssetManager.addAssetPath gets Invalid When WebView is created":
                    // let WebView replace the AssetManager first then addModuleAssets
                    // https://issuetracker.google.com/issues/140652425
                    WebView(app).destroy()
                    handleWebView = true
                }
                app.addModuleAssets()
                XposedBridge.invokeOriginalMethod(
                    param.method, param.thisObject, param.args.clone().apply {
                        this[0] = app.resources.getXml(R.xml.yt_revanced_settings)
                    })
            }
        })

    getDexMethod("licenseActivityOnCreateFingerprint") {
        findClass {
            matcher {
                className(".LicenseActivity", StringMatchType.EndsWith)
            }
        }.single().also {
            getDexMethods("licenseActivityNOTonCreate") {
                it.methods.filter { it.name != "onCreate" && it.isMethod }
            }
        }.findMethod { matcher { name = "onCreate" } }.single()
    }.hookMethod(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as Activity
            // must set theme before onCreate call super
            LicenseActivityHook.setActivityTheme(activity)
            try {
                param.invokeOriginalMethod()
            } catch (e: Throwable) {
                // ignored
            }

            activity.addModuleAssets()

            try {
                LicenseActivityHook.initialize(activity)
            } catch (_: Resources.NotFoundException) {
                AlertDialog.Builder(activity).setTitle("Restart needed")
                    .setMessage("ReVanced Xposed has been updated")
                    .setPositiveButton("Restart now") { _, _ ->
                        restartApplication(activity)
                    }.show()
            }
        }

        fun restartApplication(activity: Activity) {
            // https://stackoverflow.com/a/58530756
            val pm = activity.packageManager
            val intent = pm.getLaunchIntentForPackage(activity.packageName)
            activity.finishAffinity()
            activity.startActivity(intent)
            exitProcess(0)
        }
    })

    // Remove other methods as they will break as the onCreate method is modified above.
    getDexMethods("licenseActivityNOTonCreate").forEach {
        if (it.returnTypeName == "void") it.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    // Update shared dark mode status based on YT theme.
    // This is needed because YT allows forcing light/dark mode
    // which then differs from the system dark mode status.
    getDexMethod("setThemeFingerprint") {
        val appearanceStringId = Utils.getResourceIdentifier("app_theme_appearance_dark", "string")
        findMethod {
            matcher {
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                paramCount = 0
                addUsingNumber(appearanceStringId)
            }
        }.single { it.returnTypeName != "void" }
    }.hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            LicenseActivityHook.updateLightDarkModeStatus(param.result as Enum<*>)
        }
    })
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
        sorting = Sorting.UNSORTED,
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
        sorting = Sorting.UNSORTED,
    )
    val RETURN_YOUTUBE_DISLIKE = Screen(
        key = "revanced_settings_screen_09_return_youtube_dislike",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_09_return_youtube_dislike",
        layout = "@layout/preference_with_icon",
        sorting = Sorting.UNSORTED,
    )
    val SPONSORBLOCK = Screen(
        key = "revanced_settings_screen_10_sponsorblock",
        summaryKey = null,
        icon = "@drawable/revanced_settings_screen_10_sponsorblock",
        layout = "@layout/preference_with_icon",
        sorting = Sorting.UNSORTED,
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
        sorting = Sorting.BY_KEY,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}