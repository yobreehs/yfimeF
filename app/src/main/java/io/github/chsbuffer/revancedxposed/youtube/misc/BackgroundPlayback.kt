package io.github.chsbuffer.revancedxposed.youtube.misc

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.lang.reflect.Modifier

fun YoutubeHook.BackgroundPlayback() {
    val prefBackgroundAndOfflineCategoryId = getNumber("prefBackgroundAndOfflineCategoryId") {
        app.resources.getIdentifier(
            "pref_background_and_offline_category", "string", app.packageName
        )
    }

    val BackgroundPlaybackManagerFingerprint =
        getDexMethod("BackgroundPlaybackManagerFingerprint") {
            dexkit.findMethod {
                matcher {
                    returnType = "boolean"
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramTypes = listOf(null)
                    opNames = listOf(
                        "const/4",
                        "if-eqz",
                        "iget",
                        "and-int/lit16",
                        "if-eqz",
                        "iget-object",
                        "if-nez",
                        "sget-object",
                        "iget",
                        "const",
                        "if-ne",
                        "iget-object",
                        "if-nez",
                        "sget-object",
                        "iget",
                        "if-ne",
                        "iget-object",
                        "check-cast",
                        "goto",
                        "sget-object",
                        "goto",
                        "const/4",
                        "if-eqz",
                        "iget-boolean",
                        "if-eqz"
                    )
                }
            }.single()
        }

    val BackgroundPlaybackSettingsFingerprint = getDexMethod("BackgroundPlaybackSettingsBoolean") {
        dexkit.findMethod {
            matcher {
                returnType = "java.lang.String"
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                paramCount = 0
                opNames = listOf(
                    "invoke-virtual",
                    "move-result",
                    "invoke-virtual",
                    "move-result",
                    "if-eqz",
                    "if-nez",
                    "goto"
                )
                usingNumbers(prefBackgroundAndOfflineCategoryId)
            }
        }.single().invokes.filter { it.returnTypeName == "boolean" }[1]
    }

    XposedBridge.hookMethod(
        BackgroundPlaybackManagerFingerprint.getMethodInstance(classLoader),
        XC_MethodReplacement.returnConstant(true)
    )
    XposedBridge.hookMethod(
        BackgroundPlaybackSettingsFingerprint.getMethodInstance(classLoader),
        XC_MethodReplacement.returnConstant(true)
    )
}
