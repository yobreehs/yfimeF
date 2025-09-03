package io.github.chsbuffer.revancedxposed.youtube.video.videoid

import app.revanced.extension.shared.Logger
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

/**
 * Hooks the new video id when the video changes.
 *
 * Supports all videos (regular videos and Shorts).
 */
val videoIdHooks: MutableList<(String) -> Unit> = mutableListOf()

fun YoutubeHook.VideoId() {
    ::videoIdFingerprint.hookMethod {
        val videoIdMethod = ::PlayerResponseModel_getVideoId.method
        before { param ->
            val videoId = videoIdMethod(param.args[0]) as String
            Logger.printDebug { "setCurrentVideoId: $videoId" }
            videoIdHooks.forEach { it(videoId) }
        }
    }
}