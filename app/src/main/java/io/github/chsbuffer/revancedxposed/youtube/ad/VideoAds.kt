package io.github.chsbuffer.revancedxposed.youtube.ad

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook


fun YoutubeHook.VideoAds() {
    val LoadVideoAds = getDexMethod("LoadVideoAds") {
        dexkit.findMethod {
            matcher {
                usingEqStrings(
                    listOf(
                        "TriggerBundle doesn't have the required metadata specified by the trigger ",
                        "Tried to enter slot with no assigned slotAdapter",
                        "Trying to enter a slot when a slot of same type and physical position is already active. Its status: ",
                    )
                )
            }
        }.single()
    }

    XposedBridge.hookMethod(
        LoadVideoAds.getMethodInstance(classLoader), XC_MethodReplacement.DO_NOTHING
    )
}
