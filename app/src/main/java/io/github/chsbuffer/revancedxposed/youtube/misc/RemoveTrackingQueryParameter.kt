package io.github.chsbuffer.revancedxposed.youtube.misc

import android.content.ClipData
import android.content.Intent
import app.revanced.extension.youtube.settings.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.lang.reflect.Modifier

fun YoutubeHook.RemoveTrackingQueryParameter() {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("revanced_remove_tracking_query_parameter"),
    )

    val sanitizeArg1 = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!Settings.REMOVE_TRACKING_QUERY_PARAMETER.get()) return
            val url = param.args[1] as String
            param.args[1] = url.replace(".si=.+".toRegex(), "").replace(".feature=.+".toRegex(), "")
        }
    }

    getDexMethod("CopyTextFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opcodes(
                    Opcode.IGET_OBJECT, // Contains the text to copy to be sanitized.
                    Opcode.CONST_STRING,
                    Opcode.INVOKE_STATIC, // ClipData.newPlainText
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.IGET_OBJECT,
                    Opcode.IGET_OBJECT,
                    Opcode.INVOKE_INTERFACE,
                    Opcode.RETURN_VOID,
                )
            }
        }.single()
    }.hookMethod(
        ScopedHook(
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

    getDexMethod("YouTubeShareSheetFingerprint") {
        dexkit.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opcodes(
                    Opcode.CHECK_CAST,
                    Opcode.GOTO,
                    Opcode.MOVE_OBJECT,
                    Opcode.INVOKE_VIRTUAL,
                )
                addInvoke("Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;")
            }
        }.single()
    }.hookMethod(intentSanitizeHook)

    getDexMethod("SystemShareSheetFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "void"
                paramTypes = listOf(null, "java.util.Map")
                opcodes(
                    Opcode.CHECK_CAST,
                    Opcode.GOTO,
                )
                addEqString("YTShare_Logging_Share_Intent_Endpoint_Byte_Array")
            }
        }.single()
    }.hookMethod(intentSanitizeHook)

}
