package io.github.chsbuffer.revancedxposed.youtube.video

import app.revanced.extension.shared.settings.preference.NoTitlePreferenceCategory
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.BasePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceCategory
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen

val settingsMenuVideoQualityGroup = mutableSetOf<BasePreference>()

fun YoutubeHook.VideoQuality() {
    dependsOn(
        ::RememberVideoQuality,
        ::AdvancedVideoQualityMenu,
    )

    PreferenceScreen.VIDEO.addPreferences(
        // Keep the preferences organized together.
        PreferenceCategory(
            key = "revanced_01_video_key", // Dummy key to force the quality preferences first.
            titleKey = null,
            sorting = Sorting.UNSORTED,
            tag = NoTitlePreferenceCategory::class.java,
            preferences = settingsMenuVideoQualityGroup
        )
    )
}