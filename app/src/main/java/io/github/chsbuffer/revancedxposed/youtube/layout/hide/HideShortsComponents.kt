package io.github.chsbuffer.revancedxposed.youtube.layout.hide

import app.revanced.extension.youtube.patches.components.ShortsFilter
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.misc.addLithoFilter

fun YoutubeHook.HideShortsComponents() {
    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("revanced_hide_shorts_home"),
        SwitchPreference("revanced_hide_shorts_search"),
        SwitchPreference("revanced_hide_shorts_subscriptions"),
        SwitchPreference("revanced_hide_shorts_history"),
        // TODO: revanced_shorts_player_screen
    )

    addLithoFilter(ShortsFilter())
}