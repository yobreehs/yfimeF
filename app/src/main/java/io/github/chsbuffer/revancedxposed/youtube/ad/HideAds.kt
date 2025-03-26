package io.github.chsbuffer.revancedxposed.youtube.ad

import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.youtube.patches.components.AdsFilter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen

fun YoutubeHook.HideAds() {

    PreferenceScreen.ADS.addPreferences(
        SwitchPreference("revanced_hide_general_ads"),
//        SwitchPreference("revanced_hide_end_screen_store_banner"),
        SwitchPreference("revanced_hide_fullscreen_ads"),
        SwitchPreference("revanced_hide_buttoned_ads"),
        SwitchPreference("revanced_hide_paid_promotion_label"),
        SwitchPreference("revanced_hide_player_store_shelf"),
        SwitchPreference("revanced_hide_self_sponsor_ads"),
        SwitchPreference("revanced_hide_products_banner"),
        SwitchPreference("revanced_hide_shopping_links"),
        SwitchPreference("revanced_hide_visit_store_button"),
        SwitchPreference("revanced_hide_web_search_results"),
        SwitchPreference("revanced_hide_merchandise_banners"),
    )

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