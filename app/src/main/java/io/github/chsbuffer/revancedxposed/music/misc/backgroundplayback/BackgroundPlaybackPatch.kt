package io.github.chsbuffer.revancedxposed.music.misc.backgroundplayback

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.music.MusicHook

fun MusicHook.BackgroundPlayback() {
    ::backgroundPlaybackDisableFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
    ::kidsBackgroundPlaybackPolicyControllerFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}