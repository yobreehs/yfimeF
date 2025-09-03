package io.github.chsbuffer.revancedxposed.meta.ads

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.meta.MetaHook

fun MetaHook.HideAds() {
    ::adInjectorFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}