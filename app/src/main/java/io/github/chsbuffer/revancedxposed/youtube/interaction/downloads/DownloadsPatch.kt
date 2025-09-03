package io.github.chsbuffer.revancedxposed.youtube.interaction.downloads

import android.app.Activity
import app.revanced.extension.youtube.patches.DownloadsPatch
import app.revanced.extension.youtube.settings.preference.ExternalDownloaderPreference
import app.revanced.extension.youtube.videoplayer.ExternalDownloadButton
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.TextPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.playercontrols.ControlInitializer
import io.github.chsbuffer.revancedxposed.youtube.misc.playercontrols.PlayerControls
import io.github.chsbuffer.revancedxposed.youtube.misc.playercontrols.addBottomControl
import io.github.chsbuffer.revancedxposed.youtube.misc.playercontrols.initializeBottomControl
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.shared.mainActivityOnCreateFingerprint
import io.github.chsbuffer.revancedxposed.youtube.video.information.VideoInformationHook

fun YoutubeHook.Downloads() {

    dependsOn(
        ::PlayerControls,
        ::VideoInformationHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_external_downloader_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("revanced_external_downloader"),
                SwitchPreference("revanced_external_downloader_action_button"),
                TextPreference(
                    "revanced_external_downloader_name",
                    tag = ExternalDownloaderPreference::class.java
                ),
            ),
        ),
    )

    addBottomControl(R.layout.revanced_external_download_button)
    initializeBottomControl(
        ControlInitializer(
            R.id.revanced_external_download_button,
            ExternalDownloadButton::initializeButton,
            ExternalDownloadButton::setVisibility,
            ExternalDownloadButton::setVisibilityImmediate,
            ExternalDownloadButton::setVisibilityNegatedImmediate,
        )
    )

    ::mainActivityOnCreateFingerprint.hookMethod {
        after { DownloadsPatch.activityCreated(it.thisObject as Activity) }
    }

    ::offlineVideoEndpointFingerprint.hookMethod {
        before {
            if (DownloadsPatch.inAppDownloadButtonOnClick(it.args[2] as String)) it.result = null
        }
    }
}