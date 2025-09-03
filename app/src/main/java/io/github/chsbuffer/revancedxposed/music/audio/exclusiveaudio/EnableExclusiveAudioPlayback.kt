package io.github.chsbuffer.revancedxposed.music.audio.exclusiveaudio

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.music.MusicHook

fun MusicHook.EnableExclusiveAudioPlayback() {
    ::AllowExclusiveAudioPlaybackFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}