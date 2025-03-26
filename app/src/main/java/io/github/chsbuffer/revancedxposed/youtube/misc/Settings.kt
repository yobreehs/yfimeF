package io.github.chsbuffer.revancedxposed.youtube.misc

import android.app.Activity
import app.revanced.extension.youtube.settings.LicenseActivityHook
import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.invokeOriginalMethod
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreferenceScreen
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.IntentPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.wrap.DexMethod

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
/*
        getDexMethod("onPreferenceClick") {
            val send_feedback_key_id =
                app.resources.getIdentifier("send_feedback_key", "string", lpparam.packageName)

            dexkit.findMethod {
                matcher {
                    addUsingNumber(send_feedback_key_id)
                    paramCount = 1
                }
            }.single().also { method ->
                getDexField("androidxPreferenceGetKey") {
                    method.usingFields.single {
                        it.usingType == FieldUsingType.Read
                                && it.field.typeName == "java.lang.String"
                                && it.field.className == "androidx.preference.Preference"
                    }.field
                }
            }
        }.hookMethod(object : XC_MethodHook() {
            val getKey = getDexField("androidxPreferenceGetKey").getFieldInstance(classLoader)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val preference = param.args[0]
                val prefKey = getKey.get(preference)
                if (prefKey == "send_feedback_key") {
                    Intent().apply {
                        component = ComponentName(
                            app,
                            "com.google.android.libraries.social.licenses.LicenseActivity"
                        )
                        preference.getFirstFieldByExactType<Context>()!!.startActivity(this)
                    }
                    param.result = true
                }
            }

        })
*/

    getDexMethod("licenseActivityOnCreateFingerprint") {
        dexkit.findClass {
            matcher {
                className(".LicenseActivity$", StringMatchType.SimilarRegex)
            }
        }.single().also {
            setString("licenseActivityNOTonCreate") {
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
            LicenseActivityHook.initialize(activity)
        }
    })
    getString("licenseActivityNOTonCreate").split('|').forEach {

        val m = DexMethod(it)
//        if (m.returnTypeName == "boolean") m.hookMethod(XC_MethodReplacement.returnConstant(true))
        if (m.returnTypeName == "void") m.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    PreferenceScreen.close()
}


object PreferenceScreen : BasePreferenceScreen() {
    // Sort screens in the root menu by key, to not scatter related items apart
    // (sorting key is set in revanced_prefs.xml).
    // If no preferences are added to a screen, the screen will not be added to the settings.
    val ADS = Screen(
        key = "revanced_settings_screen_01_ads",
        summaryKey = null,
    )
    val ALTERNATIVE_THUMBNAILS = Screen(
        key = "revanced_settings_screen_02_alt_thumbnails",
        summaryKey = null,
        sorting = Sorting.UNSORTED,
    )
    val FEED = Screen(
        key = "revanced_settings_screen_03_feed",
        summaryKey = null,
    )
    val GENERAL_LAYOUT = Screen(
        key = "revanced_settings_screen_04_general",
        summaryKey = null,
    )
    val PLAYER = Screen(
        key = "revanced_settings_screen_05_player",
        summaryKey = null,
    )

    val SHORTS = Screen(
        key = "revanced_settings_screen_06_shorts",
        summaryKey = null,
    )

    val SEEKBAR = Screen(
        key = "revanced_settings_screen_07_seekbar",
        summaryKey = null,
    )
    val SWIPE_CONTROLS = Screen(
        key = "revanced_settings_screen_08_swipe_controls",
        summaryKey = null,
        sorting = Sorting.UNSORTED,
    )

    // RYD and SB are items 9 and 10.
    // Menus are added in their own patch because they use an Intent and not a Screen.

    val MISC = Screen(
        key = "revanced_settings_screen_11_misc",
        summaryKey = null,
    )
    val VIDEO = Screen(
        key = "revanced_settings_screen_12_video",
        summaryKey = null,
        sorting = Sorting.BY_KEY,
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}
