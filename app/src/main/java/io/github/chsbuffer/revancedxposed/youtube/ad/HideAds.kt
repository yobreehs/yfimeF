package io.github.chsbuffer.revancedxposed.youtube.ad

import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.patches.components.AdsFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.FixVerticalScroll
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen

fun YoutubeHook.HideAds() {
    dependsOn(
        ::FixVerticalScroll,
    )

    PreferenceScreen.ADS.addPreferences(
        SwitchPreference("revanced_hide_creator_store_shelves"),
//        SwitchPreference("revanced_hide_end_screen_store_banner"),
        SwitchPreference("revanced_hide_fullscreen_ads"),
        SwitchPreference("revanced_hide_general_ads"),
        SwitchPreference("revanced_hide_merchandise_banners"),
        SwitchPreference("revanced_hide_paid_promotion_label"),
        SwitchPreference("revanced_hide_self_sponsor_ads"),
        SwitchPreference("revanced_hide_tagged_products"),
        SwitchPreference("revanced_hide_view_products_banner"),
        SwitchPreference("revanced_hide_visit_store_button"),
        SwitchPreference("revanced_hide_web_search_results"),
    )

    // TODO: Hide end screen store banner

    // Hide ad views
    val adAttributionId = Utils.getResourceIdentifier("ad_attribution", "id")

    XposedHelpers.findAndHookMethod(
        View::class.java.name,
        lpparam.classLoader,
        "findViewById",
        Int::class.java.name,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0] == adAttributionId) {
                    Logger.printDebug { "Hide Ad Attribution View" }
                    AdsFilter.hideAdAttributionView(param.result as View)
                }
            }
        })
}