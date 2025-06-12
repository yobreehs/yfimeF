package io.github.chsbuffer.revancedxposed.youtube.ad

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.VideoAds() {
    getDexMethod("LoadVideoAds") {
        findMethod {
            matcher {
                usingEqStrings(
                    "TriggerBundle doesn't have the required metadata specified by the trigger "
                )
            }
        }.single()
    }.hookMethod(XC_MethodReplacement.DO_NOTHING)
}
