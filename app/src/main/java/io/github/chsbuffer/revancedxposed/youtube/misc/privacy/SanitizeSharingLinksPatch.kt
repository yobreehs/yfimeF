package io.github.chsbuffer.revancedxposed.youtube.misc.privacy

import io.github.chsbuffer.revancedxposed.shared.misc.privacy.SanitizeSharingLinks
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen

fun YoutubeHook.SanitizeSharingLinks() {
    SanitizeSharingLinks(preferenceScreen = PreferenceScreen.MISC)
}