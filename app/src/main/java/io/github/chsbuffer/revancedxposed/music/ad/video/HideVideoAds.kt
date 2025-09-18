package io.github.chsbuffer.revancedxposed.music.ad.video

import app.revanced.extension.music.patches.HideVideoAdsPatch
import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.music.misc.settings.PreferenceScreen
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference

fun MusicHook.HideVideoAds() {
    PreferenceScreen.ADS.addPreferences(
        SwitchPreference("revanced_music_hide_video_ads"),
    )

    ::showVideoAdsParentFingerprint.hookMethod(scopedHook(::showVideoAds.member) {
        before { param ->
            param.args[0] = HideVideoAdsPatch.showVideoAds(param.args[0] as Boolean)
        }
    })
}