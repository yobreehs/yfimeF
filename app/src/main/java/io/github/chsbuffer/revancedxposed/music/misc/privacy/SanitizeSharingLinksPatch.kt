package io.github.chsbuffer.revancedxposed.music.misc.privacy

import io.github.chsbuffer.revancedxposed.shared.misc.privacy.SanitizeSharingLinks
import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.music.misc.settings.PreferenceScreen

fun MusicHook.SanitizeSharingLinks() {
    SanitizeSharingLinks(
        preferenceScreen = PreferenceScreen.MISC,
        replaceMusicLinksWithYouTube = true
    )
}