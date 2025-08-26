package io.github.chsbuffer.revancedxposed.youtube.misc

import android.annotation.SuppressLint
import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.patches.PlayerTypeHookPatch
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier

@SuppressLint("NonUniqueDexKitData")
fun YoutubeHook.PlayerTypeHook() {

    getDexMethod("playerTypeFingerprint") {
        findClass {
            matcher {
                className(".YouTubePlayerOverlaysLayout", StringMatchType.EndsWith)
            }
        }.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC
                returnType = "void"
                paramCount = 1
            }
        }.single {
            it.paramTypes[0].superClass?.descriptor == "Ljava/lang/Enum;"
        }
    }.hookMethod {
        before { param ->
            PlayerTypeHookPatch.setPlayerType(param.args[0] as Enum<*>)
        }
    }

    getDexMethod("reelWatchPagerFingerprint") {
        findMethod {
            matcher {
                addUsingNumber(Utils.getResourceIdentifier("reel_watch_player", "id"))
            }
        }.single().also { method ->
            getDexField("ReelPlayerViewField") {
                method.declaredClass!!.fields.single { it.typeName.endsWith("ReelPlayerView") }
            }
        }
    }.hookMethod {
        val field = getDexField("ReelPlayerViewField").toField()
        after { param ->
            val thiz = param.thisObject
            val view = field.get(thiz) as View
            PlayerTypeHookPatch.onShortsCreate(view)
        }
    }

    getDexMethod("videoStateFingerprint") {
        val controlsState =
            findClass { matcher { usingStrings("controls can be in the buffering state only if in PLAYING or PAUSED video state") } }.single()
        findMethod {
            matcher {
                returnType = "void"
                addParamType { descriptor = controlsState.descriptor }
                opcodes(
                    Opcode.CONST_4,
                    Opcode.IF_EQZ,
                    Opcode.IF_EQZ,
                    Opcode.IGET_OBJECT, // obfuscated parameter field name
                )
            }
        }.also {
            if (it.count() > 1) Logger.printDebug { "multiple videoState methods found" }
        }.first().also { method ->
            getDexField("videoStateParameterField") {
                method.usingFields.distinct().single { field ->
                    // obfuscated parameter field name
                    field.usingType == FieldUsingType.Read && field.field.declaredClass == method.paramTypes[0]
                }.field
            }
        }
    }.hookMethod {
        val field = getDexField("videoStateParameterField").toField()
        before { param ->
            PlayerTypeHookPatch.setVideoState(field.get(param.args[0]) as Enum<*>)
        }
    }
}