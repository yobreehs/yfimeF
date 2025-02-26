package io.github.chsbuffer.revancedxposed.youtube.misc

import android.app.Activity
import app.revanced.extension.youtube.settings.LicenseActivityHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.wrap.DexMethod

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
                it.methods.filter { it.name != "onCreate" && it.isMethod }.joinToString("|") { it.descriptor }
            }
        }.findMethod { matcher { name = "onCreate" } }.single()
    }.hookMethod(object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            LicenseActivityHook.initialize(param.thisObject as Activity)
        }
    })
    getString("licenseActivityNOTonCreate").split('|').forEach {

        val m = DexMethod(it)
//        if (m.returnTypeName == "boolean") m.hookMethod(XC_MethodReplacement.returnConstant(true))
        if (m.returnTypeName == "void") m.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }
}