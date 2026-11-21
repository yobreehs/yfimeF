package io.github.chsbuffer.revancedxposed.spotify

import android.app.Application
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.injectHostClassLoaderToSelf
import io.github.chsbuffer.revancedxposed.settings.ModuleSettings
import io.github.chsbuffer.revancedxposed.spotify.misc.AutoHideRegionDialog
import io.github.chsbuffer.revancedxposed.spotify.misc.PlaybackDiagnostics
import io.github.chsbuffer.revancedxposed.spotify.misc.RegionProxy
import io.github.chsbuffer.revancedxposed.spotify.misc.ShowUnavailableTracks
import io.github.chsbuffer.revancedxposed.spotify.misc.UnlockPremium
import io.github.chsbuffer.revancedxposed.spotify.misc.privacy.SanitizeSharingLinks
import io.github.chsbuffer.revancedxposed.spotify.misc.widgets.FixThirdPartyLaunchersWidgets
import kotlin.reflect.KFunction0

@Suppress("UNCHECKED_CAST")
class SpotifyHook(
    internal val appContext: Application, lpparam: LoadPackageParam
) : BaseHook(appContext, lpparam) {

    init {
        // Must happen before `hooks` is built: UnlockPremium reads its flags while being selected.
        ModuleSettings.attach(appContext)
        // Must happen before Spotify's HTTP stack creates its first client. The feature is opt-in
        // and a no-op when disabled.
        RegionProxy.install(appContext)
    }

    private fun enabled(key: String) = ModuleSettings.isEnabled(key)

    override val hooks: Array<KFunction0<Unit>> = buildList {
        add(::Extension)
        if (enabled(ModuleSettings.SANITIZE_SHARING_LINKS)) add(::SanitizeSharingLinks)
        // UnlockPremium also contains the ad-blocking hooks, so it is needed if either is on.
        if (enabled(ModuleSettings.PREMIUM_UNLOCK) || enabled(ModuleSettings.BLOCK_ADS)) {
            add(::UnlockPremium)
        }
        add(::FixThirdPartyLaunchersWidgets)
        if (enabled(ModuleSettings.SHOW_UNAVAILABLE_TRACKS)) add(::ShowUnavailableTracks)
        // The abroad-dialog auto-hide is part of Block ads, not an experimental feature. The hook
        // itself also checks the experimental "force show" override.
        if (enabled(ModuleSettings.BLOCK_ADS)) add(::AutoHideRegionDialog)
        // Diagnostic-only: logs player state/errors so "track stops after a few seconds" reports can
        // be explained from `adb logcat -s revanced:*`. Changes no behaviour.
        add(::PlaybackDiagnostics)
    }.toTypedArray()

    fun Extension() {
        // load stubbed spotify classes
        injectHostClassLoaderToSelf(this::class.java.classLoader!!, classLoader)
    }
}
