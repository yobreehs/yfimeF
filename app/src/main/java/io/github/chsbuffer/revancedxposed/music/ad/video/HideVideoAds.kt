package io.github.chsbuffer.revancedxposed.music.ad.video

import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.scopedHook

fun MusicHook.HideVideoAds() {
    ::showVideoAdsParentFingerprint.hookMethod(scopedHook(::showVideoAds.member) {
        before { param ->
            param.args[0] = false
        }
    })
}