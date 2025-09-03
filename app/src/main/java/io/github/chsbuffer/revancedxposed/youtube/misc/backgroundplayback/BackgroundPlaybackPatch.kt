package io.github.chsbuffer.revancedxposed.youtube.misc.backgroundplayback

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.BackgroundPlayback() {

    ::backgroundPlaybackManagerFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))

    // Enable background playback option in YouTube settings
    ::backgroundPlaybackSettingsSubFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))

    // isBackgroundShortsPlaybackAllowed
    // Force allowing background play for Shorts.
    // Force allowing background play for videos labeled for kids.
    // Fix PiP buttons not working after locking/unlocking device screen.
    //
    // I don't get them.
}