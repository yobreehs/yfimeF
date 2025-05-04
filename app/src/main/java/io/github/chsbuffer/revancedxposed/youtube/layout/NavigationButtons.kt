package io.github.chsbuffer.revancedxposed.youtube.layout

import app.revanced.extension.youtube.patches.NavigationButtonsPatch
import io.github.chsbuffer.revancedxposed.ScopedHookSafe
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen

fun YoutubeHook.NavigationButtons() {
    val preferences = mutableSetOf(
//        SwitchPreference("revanced_hide_home_button"),
//        SwitchPreference("revanced_hide_shorts_button"),
//        SwitchPreference("revanced_hide_create_button"),
//        SwitchPreference("revanced_hide_subscriptions_button"),
//        SwitchPreference("revanced_hide_notifications_button"),
        SwitchPreference("revanced_switch_create_with_notifications_button"),
//        SwitchPreference("revanced_hide_navigation_button_labels"),
    )

    PreferenceScreen.GENERAL_LAYOUT.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_navigation_buttons_screen",
            sorting = Sorting.UNSORTED,
            preferences = preferences
        )
    )

    val ANDROID_AUTOMOTIVE_STRING = "Android Automotive"

    getDexMethod("addCreateButtonViewFingerprint") {
        findMethod {
            matcher { strings("Android Wear", ANDROID_AUTOMOTIVE_STRING) }
        }.single().also { method ->
            getDexMethod("AutoMotiveFeatureMethod") {
                method.invokes.findMethod {
                    matcher { strings("android.hardware.type.automotive") }
                }.single()
            }
        }
    }.hookMethod(
        ScopedHookSafe(getDexMethod("AutoMotiveFeatureMethod").getMethodInstance(classLoader)) {
            before { param, _ ->
                param.result = NavigationButtonsPatch.switchCreateWithNotificationButton()
            }
        })
}