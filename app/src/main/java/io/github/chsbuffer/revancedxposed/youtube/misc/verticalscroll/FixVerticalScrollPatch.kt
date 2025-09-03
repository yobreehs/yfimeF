package io.github.chsbuffer.revancedxposed.youtube.misc.verticalscroll

import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.FixVerticalScroll() {
    ::canScrollVerticallyFingerprint.hookMethod(returnConstant(false))
}