package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

fun SpotifyHook.FixThirdPartyLaunchersWidgets() {
    // Trust the calling package so third-party launchers can read the widget artwork.
    // See [mbsCallerValidationFingerprint] for the rationale / version history.
    ::mbsCallerValidationFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
