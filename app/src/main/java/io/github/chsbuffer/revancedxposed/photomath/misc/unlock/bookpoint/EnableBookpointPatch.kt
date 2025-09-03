package io.github.chsbuffer.revancedxposed.photomath.misc.unlock.bookpoint

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.photomath.PhotomathHook

fun PhotomathHook.EnableBookpoint() {
    ::isBookpointEnabledFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}