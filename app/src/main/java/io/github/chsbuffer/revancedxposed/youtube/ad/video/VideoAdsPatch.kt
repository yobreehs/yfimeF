package io.github.chsbuffer.revancedxposed.youtube.ad.video

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.VideoAds() {
    ::loadVideoAdsFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}