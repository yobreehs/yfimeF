package io.github.chsbuffer.revancedxposed.youtube.ad

import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.youtube.patches.components.AdsFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.HideAds() {
    // TODO: Hide end screen store banner

    // Hide ad views
    val adAttributionId = getNumber("adAttributionId") {
        app.resources.getIdentifier("ad_attribution", "id", app.packageName)
    }

    XposedHelpers.findAndHookMethod(
        View::class.java.name,
        lpparam.classLoader,
        "findViewById",
        Int::class.java.name,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0].equals(adAttributionId)) {
                    Logger.printInfo { "Hide Ad Attribution View" }
                    AdsFilter.hideAdAttributionView(param.result as View)
                }
            }
        })
}