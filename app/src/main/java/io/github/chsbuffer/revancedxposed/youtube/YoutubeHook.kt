package io.github.chsbuffer.revancedxposed.youtube

import android.app.Application
import android.view.LayoutInflater
import app.revanced.extension.shared.StringRef
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.addModuleAssets
import io.github.chsbuffer.revancedxposed.injectHostClassLoaderToSelf
import io.github.chsbuffer.revancedxposed.youtube.ad.general.HideAds
import io.github.chsbuffer.revancedxposed.youtube.ad.video.VideoAds
import io.github.chsbuffer.revancedxposed.youtube.interaction.copyvideourl.CopyVideoUrl
import io.github.chsbuffer.revancedxposed.youtube.interaction.downloads.Downloads
import io.github.chsbuffer.revancedxposed.youtube.interaction.swipecontrols.SwipeControls
import io.github.chsbuffer.revancedxposed.youtube.layout.buttons.action.HideButtons
import io.github.chsbuffer.revancedxposed.youtube.layout.buttons.navigation.NavigationButtons
import io.github.chsbuffer.revancedxposed.youtube.layout.hide.general.HideLayoutComponents
import io.github.chsbuffer.revancedxposed.youtube.layout.hide.shorts.HideShortsComponents
import io.github.chsbuffer.revancedxposed.youtube.layout.sponsorblock.SponsorBlock
import io.github.chsbuffer.revancedxposed.youtube.layout.startupshortsreset.DisableResumingShortsOnStartup
import io.github.chsbuffer.revancedxposed.youtube.misc.backgroundplayback.BackgroundPlayback
import io.github.chsbuffer.revancedxposed.youtube.misc.privacy.RemoveTrackingQueryParameter
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.SettingsHook
import io.github.chsbuffer.revancedxposed.youtube.video.quality.VideoQuality
import io.github.chsbuffer.revancedxposed.youtube.video.speed.PlaybackSpeed
import org.luckypray.dexkit.wrap.DexMethod

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
        ::CopyVideoUrl,
        ::Downloads,
        ::HideShortsComponents,
        ::NavigationButtons,
        ::SwipeControls,
        ::VideoQuality,
        ::DisableResumingShortsOnStartup,
        ::HideLayoutComponents,
        ::HideButtons,
        ::PlaybackSpeed,
        // make sure settingsHook at end to build preferences
        ::SettingsHook
    )

    fun ExtensionHook() {
        injectHostClassLoaderToSelf(this::class.java.classLoader!!, classLoader)
        DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;").hookMethod{
            before {
                (it.thisObject as LayoutInflater).context.addModuleAssets()
            }
        }
        val app = Utils.getContext()
        app.addModuleAssets()
        StringRef.resources = app.resources
        StringRef.packageName = BuildConfig.APPLICATION_ID
        StringRef.packageName2 = app.packageName
    }
}
