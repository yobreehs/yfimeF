package io.github.chsbuffer.revancedxposed.youtube.misc

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Resources
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.ThemeHelper
import app.revanced.extension.youtube.settings.LicenseActivityHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.ScopedHookSafe
import io.github.chsbuffer.revancedxposed.addModuleAssets
import io.github.chsbuffer.revancedxposed.invokeOriginalMethod
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreferenceScreen
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.IntentPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.wrap.DexMethod
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
    getDexMethod("PreferenceFragmentCompat#addPreferencesFromResource") {
        findClass {
            matcher {
                usingStrings(
                    "Could not create RecyclerView",
                    "Content has view with id attribute 'android.R.id.list_container' that is not a ViewGroup class",
                    "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT"
                )
            }
        }.findMethod {
            matcher {
                returnType = "void"
                paramTypes("int")
            }
        }.single().also { method ->
            getDexMethod("PreferenceInflater#inflate") {
                method.invokes.single {
                    it.paramTypes.getOrNull(0)?.name == "org.xmlpull.v1.XmlPullParser"
                }
            }
        }
    }.hookMethod(
        ScopedHookSafe(getDexMethod("PreferenceInflater#inflate").toMethod()) {
            after { param, outerParam ->
                val preferencesName = app.resources.getResourceName(outerParam.args[0] as Int)
                Logger.printDebug { "addPreferencesFromResource $preferencesName" }
                if (!preferencesName.contains("settings_fragment")) return@after
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
            getString("licenseActivityNOTonCreate") {
                it.methods.filter { it.name != "onCreate" && it.isMethod }
                    .joinToString("|") { it.descriptor }
            }
        }.findMethod { matcher { name = "onCreate" } }.single()
    }.hookMethod(object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam) {
            try {
                param.invokeOriginalMethod()
            } catch (e: Throwable) {
                // ignored
            }

            val activity = param.thisObject as Activity
            activity.addModuleAssets()

            try {
                LicenseActivityHook.initialize(activity)
            } catch (_: Resources.NotFoundException) {
                AlertDialog.Builder(activity)
                    .setTitle("Restart needed")
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
    getString("licenseActivityNOTonCreate").split('|').forEach {
        val m = DexMethod(it)
        if (m.returnTypeName == "void") m.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

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
            ThemeHelper.setTheme(param.result as Enum<*>)
        }
    })

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