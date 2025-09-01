package io.github.chsbuffer.revancedxposed.youtube

import android.app.Application
import app.revanced.extension.shared.StringRef
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.addModuleAssets
import io.github.chsbuffer.revancedxposed.injectHostClassLoaderToSelf
import io.github.chsbuffer.revancedxposed.youtube.ad.HideAds
import io.github.chsbuffer.revancedxposed.youtube.ad.VideoAds
import io.github.chsbuffer.revancedxposed.youtube.interaction.SwipeControls
import io.github.chsbuffer.revancedxposed.youtube.layout.NavigationButtons
import io.github.chsbuffer.revancedxposed.youtube.layout.SponsorBlock
import io.github.chsbuffer.revancedxposed.youtube.layout.hide.HideShortsComponents
import io.github.chsbuffer.revancedxposed.youtube.misc.BackgroundPlayback
import io.github.chsbuffer.revancedxposed.youtube.misc.RemoveTrackingQueryParameter
import io.github.chsbuffer.revancedxposed.youtube.misc.SettingsHook
import io.github.chsbuffer.revancedxposed.youtube.video.VideoQuality

class YoutubeHook(
    app: Application,
    lpparam: LoadPackageParam
) : BaseHook(app, lpparam) {

    override val hooks = arrayOf(
        ::ExtensionHook,
        ::VideoAds,
        ::BackgroundPlayback,
        ::RemoveTrackingQueryParameter,
        ::HideAds,
        ::SponsorBlock,
        ::HideShortsComponents,
        ::NavigationButtons,
        ::SwipeControls,
        ::VideoQuality,
        // make sure settingsHook at end to build preferences
        ::SettingsHook
    )

    fun ExtensionHook() {
        Utils.setContext(app)
        injectHostClassLoaderToSelf(this::class.java.classLoader!!, classLoader)
        app.addModuleAssets()
        StringRef.resources = app.resources
        StringRef.packageName = BuildConfig.APPLICATION_ID
    }
}
