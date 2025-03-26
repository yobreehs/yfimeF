package io.github.chsbuffer.revancedxposed.youtube

import android.app.Application
import android.content.res.Resources
import android.content.res.XModuleResources
import app.revanced.extension.shared.StringRef
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.youtube.ad.HideAds
import io.github.chsbuffer.revancedxposed.youtube.ad.VideoAds
import io.github.chsbuffer.revancedxposed.youtube.layout.SponsorBlock
import io.github.chsbuffer.revancedxposed.youtube.misc.BackgroundPlayback
import io.github.chsbuffer.revancedxposed.youtube.misc.LithoFilter
import io.github.chsbuffer.revancedxposed.youtube.misc.RemoveTrackingQueryParameter

lateinit var modRes: Resources

class YoutubeHook(
    app: Application,
    lpparam: LoadPackageParam,
    val resparam: XC_InitPackageResources.InitPackageResourcesParam,
    val startupParam: IXposedHookZygoteInit.StartupParam
) : BaseHook(app, lpparam) {


    override val hooks = arrayOf(
        ::ExtensionHook,
        ::VideoAds,
        ::BackgroundPlayback,
        ::RemoveTrackingQueryParameter,
        ::HideAds,
        ::LithoFilter,
        ::SponsorBlock
    )

    fun ExtensionHook() {
        Utils.setContext(app)
        modRes = XModuleResources.createInstance(startupParam.modulePath, resparam.res)

        StringRef.resources = modRes
        StringRef.packageName = BuildConfig.APPLICATION_ID
    }
}
