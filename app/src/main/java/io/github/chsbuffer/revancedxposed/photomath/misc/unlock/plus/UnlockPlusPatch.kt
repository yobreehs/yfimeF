package io.github.chsbuffer.revancedxposed.photomath.misc.unlock.plus

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.photomath.PhotomathHook
import io.github.chsbuffer.revancedxposed.photomath.misc.unlock.bookpoint.EnableBookpoint

fun PhotomathHook.UnlockPlus() {
    dependsOn(::EnableBookpoint)
    ::isPlusUnlockedFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}