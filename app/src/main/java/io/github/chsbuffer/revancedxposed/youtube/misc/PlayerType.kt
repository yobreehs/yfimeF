package io.github.chsbuffer.revancedxposed.youtube.misc

import android.view.View
import app.revanced.extension.youtube.patches.PlayerTypeHookPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.FieldUsingType

fun YoutubeHook.PlayerTypeHook() {

    getDexMethod("playerTypeFingerprint") {
        dexkit.findClass {
            matcher {
                className(".YouTubePlayerOverlaysLayout$", StringMatchType.SimilarRegex)
            }
        }.findMethod {
            matcher {
                returnType = "void"
                paramCount = 1
                opcodes(
                    Opcode.IF_NE,
                    Opcode.RETURN_VOID,
                )
            }
        }.single {
            // enum class
            it.paramTypes[0].descriptor.length > 1
        }
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            PlayerTypeHookPatch.setPlayerType(param.args[0] as Enum<*>)
        }
    })

    getDexMethod("reelWatchPagerFingerprint") {
        dexkit.findMethod {
            matcher {
                addUsingNumber(
                    app.resources.getIdentifier(
                        "reel_watch_player", "id", lpparam.packageName
                    )
                )
            }
        }.single().also { method ->
            getDexField("ReelPlayerViewField") {
                method.declaredClass!!.fields.single { it.typeName.endsWith("ReelPlayerView") }
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        val field = getDexField("ReelPlayerViewField").getFieldInstance(classLoader)
        override fun afterHookedMethod(param: MethodHookParam) {
            val thiz = param.thisObject
            val view = field.get(thiz) as View
            PlayerTypeHookPatch.onShortsCreate(view)
        }
    })

    getDexMethod("videoStateFingerprint") {
        dexkit.findMethod {
            matcher {
                returnType = "void"
                paramTypes("com/google/android/libraries/youtube/player/features/overlay/controls/ControlsState")
                opcodes(
                    Opcode.CONST_4,
                    Opcode.IF_EQZ,
                    Opcode.IF_EQZ,
                    Opcode.IGET_OBJECT, // obfuscated parameter field name
                )
            }
        }.single().also { method ->
            setString("videoStateParameterField") {
                method.usingFields.distinct().single { field ->
                    // obfuscated parameter field name
                    field.usingType == FieldUsingType.Read && field.field.declaredClass == method.paramTypes[0]
                }.field
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        val field = getDexField("videoStateParameterField").getFieldInstance(classLoader)
        override fun beforeHookedMethod(param: MethodHookParam) {
            PlayerTypeHookPatch.setVideoState(field.get(param.args[0]) as Enum<*>)
        }
    })
}