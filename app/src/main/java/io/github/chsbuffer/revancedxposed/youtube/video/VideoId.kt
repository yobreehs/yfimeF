package io.github.chsbuffer.revancedxposed.youtube.video

import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

/**
 * Hooks the new video id when the video changes.
 *
 * Supports all videos (regular videos and Shorts).
 */
val videoIdHooks: MutableList<(String) -> Unit> = mutableListOf()


fun YoutubeHook.VideoIdPatch() {
    getDexMethod("videoId") {
        findMethod {
            matcher {
                addEqString("Null initialPlayabilityStatus")
            }
        }.single().also { method ->
            getDexMethod("PlayerResponseModel.videoId") {
                method.invokes.distinct().single {
                    it.returnTypeName == "java.lang.String" && it.declaredClass == method.paramTypes[0] // PlayerResponseModel, interface
                }
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        val videoIdMethod =
            getDexMethod("PlayerResponseModel.videoId").getMethodInstance(classLoader)

        override fun beforeHookedMethod(param: MethodHookParam) {
            val videoId = videoIdMethod(param.args[0]) as String
            videoIdHooks.forEach { it(videoId) }
        }
    })
}

/*

fun YoutubeHook.VideoIdPatchTest() {

    val LogCall: (DexKitBridge.() -> DexMethod) -> Unit = {
        val dexMethod = it(dexkit)
        XposedBridge.hookMethod(dexMethod.getMethodInstance(classLoader), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                Logger.printDebug { "$dexMethod" }
            }
        })
    }

    val videoIdParentFingerprint = findMethod {
        matcher {
            modifiers = Modifier.PUBLIC or Modifier.FINAL
            paramCount = 1
            addUsingNumber(524288L)
        }
    }.single { it.returnType!!.isArray }

    val playerResponseModelStringMatcher = MethodMatcher().apply {
        declaredClass =
            "com/google/android/libraries/youtube/innertube/model/player/PlayerResponseModel"
        returnType = "java/lang/String"
    }
    val videoIdFingerprint = videoIdParentFingerprint.declaredClass!!.findMethod {
        matcher {
            returnType = "void"
            paramCount = 1
            opcodes(
                Opcode.INVOKE_INTERFACE,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_INTERFACE,
                Opcode.MOVE_RESULT_OBJECT,
            )
            addInvoke(playerResponseModelStringMatcher)
        }
    }.single()

    LogCall { videoIdFingerprint.toDexMethod() }

    val videoIdBackgroundPlayFingerprint = findMethod {
                matcher {
                    opcodes(
                        Opcode.IF_EQZ,
                        Opcode.INVOKE_INTERFACE,
                        Opcode.MOVE_RESULT_OBJECT,
                        Opcode.IPUT_OBJECT,
                        Opcode.MONITOR_EXIT,
                        Opcode.RETURN_VOID,
                        Opcode.MONITOR_EXIT,
                        Opcode.RETURN_VOID
                    )
                    returnType = "void"
                    paramCount = 1
                }
            }
    // disable cache
    throw Exception()
}
*/

