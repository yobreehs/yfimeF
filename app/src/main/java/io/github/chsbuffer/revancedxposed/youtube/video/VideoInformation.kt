package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.youtube.patches.VideoInformation
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.getStaticObjectField
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.OpCodeMatchType
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Hook the player controller.  Called when a video is opened or the current video is changed.
 *
 * Note: This hook is called very early and is called before the video id, video time, video length,
 * and many other data fields are set.
 *
 * @param targetMethodClass The descriptor for the class to invoke when the player controller is created.
 * @param targetMethodName The name of the static method to invoke when the player controller is created.
 */
val playerInitHooks = mutableListOf<(VideoInformation.PlaybackController) -> Unit>()
val videoTimeHooks = mutableListOf<(Long) -> Unit>()

class PlaybackController(
    private val obj: Any,
    private val seekTo: Method,
    private val seekToRelative: Method,
    val seekSourceNone: Any
) : VideoInformation.PlaybackController {
    override fun seekTo(videoTime: Long): Boolean {
        return seekTo.invoke(obj, videoTime, seekSourceNone) as Boolean
    }

    override fun seekToRelative(videoTimeOffset: Long) {
        seekToRelative.invoke(obj)
    }
}

fun YoutubeHook.VideoInformationHook() {
    dependsOn(
        ::VideoIdPatch,
        ::PlayerResponseMethodHook,
    )

    //region playerController
    val playerInitMethod = getDexMethod("playerInitMethod") {
        dexkit.findClass {
            matcher {
                addEqString("playVideo called on player response with no videoStreamingData.")
            }
        }.single().methods.single { it.name == "<init>" }.also {
            val playerClass = it.declaredClass!!
            getDexMethod("seekFingerprint") {
                playerClass.findMethod {
                    matcher { addEqString("Attempting to seek during an ad") }
                }.single().also {
                    getDexClass("seekSourceType") { it.paramTypes[1] }
                }
            }
            getDexMethod("seekRelativeFingerprint") {
                playerClass.findMethod {
                    matcher {
                        modifiers = Modifier.FINAL or Modifier.PUBLIC
                        paramTypes("long", null)
                        opcodes(
                            Opcode.ADD_LONG_2ADDR,
                            Opcode.INVOKE_VIRTUAL,
                        )
                    }
                }.single()
            }
        }
    }

    playerInitMethod.apply {
        val seekSourceType = getDexClass("seekSourceType").getInstance(classLoader)
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod(object : XC_MethodHook() {
            val seekFingerprint = getDexMethod("seekFingerprint").getMethodInstance(classLoader)
            val seekRelativeFingerprint =
                getDexMethod("seekRelativeFingerprint").getMethodInstance(classLoader)

            var playerController: PlaybackController? = null

            override fun afterHookedMethod(param: MethodHookParam) {
                playerController = PlaybackController(
                    param.thisObject, seekFingerprint, seekRelativeFingerprint, seekSourceNone
                )
                playerInitHooks.forEach { it(playerController!!) }
            }
        })
    }

    playerInitHooks.add { VideoInformation.initialize(it) }
    //endregion

    //region mdxPlayerDirector
    val mdxInitMethod = getDexMethod("mdxPlayerDirectorSetVideoStageFingerprint") {
        dexkit.findClass {
            matcher {
                addEqString("MdxDirector setVideoStage ad should be null when videoStage is not an Ad state ")
            }
        }.single().methods.single { it.name == "<init>" }.also {
            val mdxClass = it.declaredClass!!
            getDexMethod("mdxSeekFingerprint") {
                mdxClass.findMethod {
                    matcher {
                        modifiers = Modifier.FINAL or Modifier.PUBLIC
                        returnType("boolean")
                        paramTypes("long", null)
                        opcodes(
                            Opcode.INVOKE_VIRTUAL,
                            Opcode.MOVE_RESULT,
                            Opcode.RETURN,
                        ).apply {
                            // The instruction count is necessary here to avoid matching the relative version
                            // of the seek method we're after, which has the same function signature as the
                            // regular one, is in the same class, and even has the exact same 3 opcodes pattern.
                            matchType = OpCodeMatchType.Equals
                        }
                    }
                }.single().also {
                    getDexClass("mkxSeekSourceType") { it.paramTypes[1] }
                }
            }
            getDexMethod("mdxSeekRelativeFingerprint") {
                mdxClass.findMethod {
                    matcher {
                        modifiers = Modifier.FINAL or Modifier.PUBLIC
                        paramTypes("long", null)
                        opcodes(
                            Opcode.IGET_OBJECT,
                            Opcode.INVOKE_INTERFACE,
                        )
                    }
                }.single()
            }
        }
    }

    mdxInitMethod.apply {

        val seekSourceType = getDexClass("mkxSeekSourceType").getInstance(classLoader)
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod(object : XC_MethodHook() {
            val mdxSeekFingerprint =
                getDexMethod("mdxSeekFingerprint").getMethodInstance(classLoader)
            val mdxSeekRelativeFingerprint =
                getDexMethod("mdxSeekRelativeFingerprint").getMethodInstance(classLoader)

            var playerController: PlaybackController? = null
            override fun afterHookedMethod(param: MethodHookParam) {
                playerController = PlaybackController(
                    param.thisObject, mdxSeekFingerprint, mdxSeekRelativeFingerprint, seekSourceNone
                )
                VideoInformation.initializeMdx(playerController!!)
            }
        })
    }
    //endregion

    getDexMethod("videoLengthFingerprint") {
//        // createVideoPlayerSeekbarFingerprint
//        dexkit.findMethod {
//            matcher {
//                addEqString("timed_markers_width")
//                returnType = "void"
//            }
//        }.single().declaredClass
        dexkit.findMethod {
            matcher {
                opcodes(
                    Opcode.MOVE_RESULT_WIDE,
                    Opcode.CMP_LONG,
                    Opcode.IF_LEZ,
                    Opcode.IGET_OBJECT,
                    Opcode.CHECK_CAST,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT_WIDE,
                    Opcode.GOTO,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT_WIDE,
                    Opcode.CONST_4,
                    Opcode.INVOKE_VIRTUAL,
                )
            }
        }.single().also { method ->
            val videoLengthField =
                method.usingFields.single { it.usingType == FieldUsingType.Write && it.field.typeName == "long" }.field
            val videoLengthHolderField =
                method.usingFields.single { it.usingType == FieldUsingType.Read && it.field.typeName == videoLengthField.declaredClassName }.field
            getDexField("videoLengthField") { videoLengthField }
            getDexField("videoLengthHolderField") { videoLengthHolderField }
        }
    }.hookMethod(object : XC_MethodHook() {
        val videoLengthField = getDexField("videoLengthField").getFieldInstance(classLoader)
        val videoLengthHolderField =
            getDexField("videoLengthHolderField").getFieldInstance(classLoader)

        override fun afterHookedMethod(param: MethodHookParam) {
            val videoLengthHolder = videoLengthHolderField.get(param.thisObject)
            val videoLength = videoLengthField.getLong(videoLengthHolder)
            VideoInformation.setVideoLength(videoLength)
        }
    })

    /*
     * Inject call for video ids
     */
    videoIdHooks.add { VideoInformation.setVideoId(it) }
    playerResponseVideoIdHooks.add { id, z -> VideoInformation.setPlayerResponseVideoId(id, z) }

    // Call before any other video id hooks,
    // so they can use VideoInformation and check if the video id is for a Short.
    playerResponseBeforeVideoIdHooks.add { protobuf, videoId, isShortAndOpeningOrPlaying ->
        VideoInformation.newPlayerResponseSignature(
            protobuf, videoId, isShortAndOpeningOrPlaying
        )
    }

    /*
     * Set the video time method
     */
    getDexMethod("timeMethod") {
        dexkit.findMethod {
            matcher {
                opcodes(Opcode.INVOKE_DIRECT_RANGE, Opcode.IGET_OBJECT)
                strings("Media progress reported outside media playback: ")
            }
        }.single().invokes.single { it.name == "<init>" }
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val videoTime = param.args[0] as Long
            videoTimeHooks.forEach { it(videoTime) }
        }
    })

    videoTimeHooks.add { videoTime ->
        VideoInformation.setVideoTime(videoTime)
    }

    // TODO Hook the user playback speed selection.

    // TODO Handle new playback speed menu.
}
