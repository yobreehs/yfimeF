package io.github.chsbuffer.revancedxposed.youtube.misc

import android.content.ClipData
import android.content.Intent
import app.revanced.extension.youtube.settings.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.lang.reflect.Modifier

fun YoutubeHook.RemoveTrackingQueryParameter() {

    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("revanced_remove_tracking_query_parameter"),
    )

    val CopyTextFingerprint = getDexMethod("CopyTextFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opNames = listOf(
                    "iget-object",
                    "const-string",
                    "invoke-static",
                    "move-result-object",
                    "invoke-virtual",
                    "iget-object",
                    "iget-object",
                    "invoke-interface",
                    "return-void",
                )
            }
        }.single()
    }

    val sanitizeArg1 = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!Settings.REMOVE_TRACKING_QUERY_PARAMETER.get()) return
            val url = param.args[1] as String
            param.args[1] =
                url.replace(".si=.+".toRegex(), "").replace(".feature=.+".toRegex(), "")
        }
    }

    XposedBridge.hookMethod(
        CopyTextFingerprint.getMethodInstance(classLoader), ScopedHook(
            XposedHelpers.findMethodExact(
                ClipData::class.java.name,
                lpparam.classLoader,
                "newPlainText",
                CharSequence::class.java,
                CharSequence::class.java
            ), sanitizeArg1
        )
    )

    val intentSanitizeHook = ScopedHook(
        XposedHelpers.findMethodExact(
            Intent::class.java.name,
            lpparam.classLoader,
            "putExtra",
            String::class.java,
            String::class.java
        ), sanitizeArg1
    )

    val YouTubeShareSheetFingerprint = getDexMethod("YouTubeShareSheetFingerprint") {
        dexkit.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opNames = listOf(
                    "check-cast",
                    "goto",
                    "move-object",
                    "invoke-virtual",
                )
                addInvoke("Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;")
            }
        }.single()
    }

    XposedBridge.hookMethod(
        YouTubeShareSheetFingerprint.getMethodInstance(classLoader), intentSanitizeHook
    )

    val SystemShareSheetFingerprint = getDexMethod("SystemShareSheetFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opNames = listOf(
                    "check-cast",
                    "goto",
                )
                addEqString("YTShare_Logging_Share_Intent_Endpoint_Byte_Array")
            }
        }.single()
    }

    XposedBridge.hookMethod(
        SystemShareSheetFingerprint.getMethodInstance(classLoader), intentSanitizeHook
    )
}
