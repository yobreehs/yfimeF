package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.youtube.patches.VideoInformation
import com.google.android.libraries.youtube.innertube.model.media.VideoQuality
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.getStaticObjectField
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.OpCodeMatchType
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.wrap.DexClass
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
    override fun patch_seekTo(videoTime: Long): Boolean {
        return seekTo.invoke(obj, videoTime, seekSourceNone) as Boolean
    }

    override fun patch_seekToRelative(videoTimeOffset: Long) {
        seekToRelative.invoke(obj)
    }
}

private lateinit var getQualityName: (VideoQuality) -> String
private lateinit var getResolution: (VideoQuality) -> Int

fun VideoQuality.getResolution() = getResolution(this)
fun VideoQuality.getQualityName() = getQualityName(this)

fun YoutubeHook.VideoInformationHook() {
    dependsOn(
        ::VideoIdPatch,
        ::PlayerResponseMethodHook,
    )

    //region playerController
    val playerInitMethod = getDexMethod("playerInitMethod") {
        findClass {
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
        val seekSourceType = getDexClass("seekSourceType").toClass()
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod(object : XC_MethodHook() {
            val seekFingerprint = getDexMethod("seekFingerprint").toMethod()
            val seekRelativeFingerprint =
                getDexMethod("seekRelativeFingerprint").toMethod()

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
        findClass {
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

        val seekSourceType = getDexClass("mkxSeekSourceType").toClass()
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod(object : XC_MethodHook() {
            val mdxSeekFingerprint =
                getDexMethod("mdxSeekFingerprint").toMethod()
            val mdxSeekRelativeFingerprint =
                getDexMethod("mdxSeekRelativeFingerprint").toMethod()

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
//        findMethod {
//            matcher {
//                addEqString("timed_markers_width")
//                returnType = "void"
//            }
//        }.single().declaredClass
        findMethod {
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
        val videoLengthField = getDexField("videoLengthField").toField()
        val videoLengthHolderField =
            getDexField("videoLengthHolderField").toField()

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
        findMethod {
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

    // videoQuality
    val YOUTUBE_VIDEO_QUALITY_CLASS_TYPE =
        "Lcom/google/android/libraries/youtube/innertube/model/media/VideoQuality;"

    val videoQualityClass = DexClass(YOUTUBE_VIDEO_QUALITY_CLASS_TYPE).toClass()
    val qualityNameField = videoQualityClass.findFirstFieldByExactType(String::class.java)
    val resolutionField = videoQualityClass.findFirstFieldByExactType(Int::class.java)

    getQualityName = { quality -> qualityNameField.get(quality) as String }
    getResolution = { quality -> resolutionField.get(quality) as Int }

    // Fix bad data used by YouTube.
    XposedBridge.hookAllConstructors(
        videoQualityClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val quality = param.thisObject as VideoQuality
                val newResolution = VideoInformation.fixVideoQualityResolution(
                    quality.getQualityName(), quality.getResolution()
                )
                resolutionField.set(quality, newResolution)
            }
        })

    val videoQualitySetterFingerprint = getDexMethod("videoQualitySetterFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("V")
            parameters("[L", "I", "Z")
            opcodes(
                Opcode.IF_EQZ,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.IPUT_BOOLEAN,
            )
            strings("menu_item_video_quality")
        }
    }

    getDexMethod("setVideoQualityFingerprint") {
        fingerprint {
            returns("V")
            parameters("L")
            opcodes(
                Opcode.IGET_OBJECT,
                Opcode.IPUT_OBJECT,
                Opcode.IGET_OBJECT,
            )
            classMatcher {
                className = videoQualitySetterFingerprint.className
            }
        }.also { method ->
            val usingFields = method.usingFields
            getDexField("onItemClickListenerClassReference") {
                usingFields[0].field
            }
            getDexField("setQualityFieldReference") {
                usingFields[1].field
            }
            getDexMethod("setQualityMenuIndexMethod") {
                usingFields[1].field.type.findMethod {
                    matcher { addParamType { descriptor = YOUTUBE_VIDEO_QUALITY_CLASS_TYPE } }
                }.single()
            }
        }
    }

    // Detect video quality changes and override the current quality.
    videoQualitySetterFingerprint.hookMethod(object : XC_MethodHook() {
        val onItemClickListenerClass = getDexField("onItemClickListenerClassReference").toField()
        val setQualityField = getDexField("setQualityFieldReference").toField()
        val setQualityMenuIndexMethod = getDexMethod("setQualityMenuIndexMethod").toMethod()

        @Suppress("UNCHECKED_CAST")
        override fun beforeHookedMethod(param: MethodHookParam) {
            val qualities = param.args[0] as Array<out VideoQuality>
            val originalQualityIndex = param.args[1] as Int
            val menu = param.thisObject.let { onItemClickListenerClass.get(it) }
                .let { setQualityField.get(it) }

            param.args[1] = VideoInformation.setVideoQuality(
                qualities,
                { quality -> setQualityMenuIndexMethod(menu, quality) },
                originalQualityIndex
            )
        }
    })
}
