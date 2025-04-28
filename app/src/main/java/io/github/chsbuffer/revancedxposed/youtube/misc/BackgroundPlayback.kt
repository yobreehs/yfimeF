package io.github.chsbuffer.revancedxposed.youtube.misc

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.lang.reflect.Modifier

fun YoutubeHook.BackgroundPlayback() {
    val prefBackgroundAndOfflineCategoryId = getNumber("prefBackgroundAndOfflineCategoryId") {
        app.resources.getIdentifier(
            "pref_background_and_offline_category", "string", app.packageName
        )
    }

    // isBackgroundPlaybackAllowed
    getDexMethod("BackgroundPlaybackManagerFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "boolean"
                modifiers = Modifier.PUBLIC or Modifier.STATIC
                paramTypes = listOf(null)
                opcodes(
                    Opcode.CONST_4,
                    Opcode.IF_EQZ,
                    Opcode.IGET,
                    Opcode.AND_INT_LIT16,
                    Opcode.IF_EQZ,
                    Opcode.IGET_OBJECT,
                    Opcode.IF_NEZ,
                    Opcode.SGET_OBJECT,
                    Opcode.IGET,
                    Opcode.CONST,
                    Opcode.IF_NE,
                    Opcode.IGET_OBJECT,
                    Opcode.IF_NEZ,
                    Opcode.SGET_OBJECT,
                    Opcode.IGET,
                    Opcode.IF_NE,
                    Opcode.IGET_OBJECT,
                    Opcode.CHECK_CAST,
                    Opcode.GOTO,
                    Opcode.SGET_OBJECT,
                    Opcode.GOTO,
                    Opcode.CONST_4,
                    Opcode.IF_EQZ,
                    Opcode.IGET_BOOLEAN,
                    Opcode.IF_EQZ,
                )
            }
        }.single()
    }.hookMethod(XC_MethodReplacement.returnConstant(true))

    // Enable background playback option in YouTube settings
    getDexMethod("BackgroundPlaybackSettingsBoolean") {
        dexkit.findMethod {
            matcher {
                returnType = "java.lang.String"
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                paramCount = 0
                opcodes(
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT,
                    Opcode.IF_EQZ,
                    Opcode.IF_NEZ,
                    Opcode.GOTO,
                )
                usingNumbers(prefBackgroundAndOfflineCategoryId)
            }
        }.single().invokes.filter { it.returnTypeName == "boolean" }[1]
    }.hookMethod(XC_MethodReplacement.returnConstant(true))

    // isBackgroundShortsPlaybackAllowed
    // Force allowing background play for Shorts.
    // Force allowing background play for videos labeled for kids.
    // Fix PiP buttons not working after locking/unlocking device screen.
    //
    // I don't get them.
}
