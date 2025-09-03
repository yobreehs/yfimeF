package io.github.chsbuffer.revancedxposed.youtube.video.information

import app.revanced.extension.youtube.patches.VideoInformation
import com.google.android.libraries.youtube.innertube.model.media.VideoQuality
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.getStaticObjectField
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.video.playerresponse.PlayerResponseMethodHook
import io.github.chsbuffer.revancedxposed.youtube.video.playerresponse.playerResponseBeforeVideoIdHooks
import io.github.chsbuffer.revancedxposed.youtube.video.playerresponse.playerResponseVideoIdHooks
import io.github.chsbuffer.revancedxposed.youtube.video.videoid.VideoId
import io.github.chsbuffer.revancedxposed.youtube.video.videoid.videoIdHooks
import org.luckypray.dexkit.wrap.DexClass
import java.lang.reflect.Method

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
        ::VideoId,
        ::PlayerResponseMethodHook,
    )

    //region playerController
    ::playerInitFingerprint.apply {
        val seekSourceType = ::seekSourceType.clazz
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod {
            val seekFingerprint = ::seekFingerprint.method
            val seekRelativeFingerprint = ::seekRelativeFingerprint.method

            after { param ->
                val playerController = PlaybackController(
                    param.thisObject, seekFingerprint, seekRelativeFingerprint, seekSourceNone
                )
                playerInitHooks.forEach { it(playerController) }
            }
        }
    }

    playerInitHooks.add { VideoInformation.initialize(it) }
    //endregion

    //region mdxPlayerDirector
    ::mdxPlayerDirectorSetVideoStageFingerprint.apply {
        val seekSourceType = ::mdxSeekSourceType.clazz
        val seekSourceNone = seekSourceType.getStaticObjectField("a")!!
        hookMethod {
            val mdxSeekFingerprint = ::mdxSeekFingerprint.method
            val mdxSeekRelativeFingerprint = ::mdxSeekRelativeFingerprint.method

            after { param ->
                val playerController = PlaybackController(
                    param.thisObject, mdxSeekFingerprint, mdxSeekRelativeFingerprint, seekSourceNone
                )
                VideoInformation.initializeMdx(playerController)
            }
        }
    }
    //endregion

    ::videoLengthFingerprint.hookMethod {
        val videoLengthField = ::videoLengthField.field
        val videoLengthHolderField = ::videoLengthHolderField.field

        after { param ->
            val videoLengthHolder = videoLengthHolderField.get(param.thisObject)
            val videoLength = videoLengthField.getLong(videoLengthHolder)
            VideoInformation.setVideoLength(videoLength)
        }
    }

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
    ::timeMethod.hookMethod {
        before { param ->
            val videoTime = param.args[0] as Long
            videoTimeHooks.forEach { it(videoTime) }
        }
    }

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

    // Detect video quality changes and override the current quality.
    ::videoQualitySetterFingerprint.hookMethod {
        val onItemClickListenerClass = ::onItemClickListenerClassReference.field
        val setQualityField = ::setQualityFieldReference.field
        val setQualityMenuIndexMethod = ::setQualityMenuIndexMethod.method

        @Suppress("UNCHECKED_CAST") before { param ->
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
    }
}