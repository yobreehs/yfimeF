package io.github.chsbuffer.revancedxposed.youtube.interaction

import android.app.Activity
import app.revanced.extension.youtube.patches.DownloadsPatch
import app.revanced.extension.youtube.settings.preference.ExternalDownloaderPreference
import app.revanced.extension.youtube.videoplayer.ExternalDownloadButton
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.TextPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.BottomControl
import io.github.chsbuffer.revancedxposed.youtube.misc.PlayerControls
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.misc.addBottomControl
import io.github.chsbuffer.revancedxposed.youtube.misc.initializeBottomControl
import io.github.chsbuffer.revancedxposed.youtube.video.VideoInformationHook
import org.luckypray.dexkit.query.enums.StringMatchType

fun YoutubeHook.Downloads() {

    dependsOn(
        ::PlayerControls,
        ::VideoInformationHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_external_downloader_screen",
            sorting = Sorting.UNSORTED,
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
        BottomControl(
            R.id.revanced_external_download_button,
            ExternalDownloadButton::initializeButton,
            ExternalDownloadButton::setVisibility,
            ExternalDownloadButton::setVisibilityImmediate,
            ExternalDownloadButton::setVisibilityNegatedImmediate,
        )
    )

    getDexMethod("mainActivityOnCreateFingerprint") {
        fingerprint {
            returns("V")
            parameters("Landroid/os/Bundle;")
            methodMatcher { name = "onCreate" }
            classMatcher { className(".MainActivity", StringMatchType.EndsWith) }
        }
    }.hookMethod { after { DownloadsPatch.activityCreated(it.thisObject as Activity) } }

    getDexMethod("offlineVideoEndpointFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("V")
            parameters(
                "Ljava/util/Map;",
                "L",
                "Ljava/lang/String", // VideoId
                "L",
            )
            strings("Object is not an offlineable video: ")
        }
    }.hookMethod {
        before {
            if (DownloadsPatch.inAppDownloadButtonOnClick(it.args[2] as String))
                it.result = null
        }
    }
}