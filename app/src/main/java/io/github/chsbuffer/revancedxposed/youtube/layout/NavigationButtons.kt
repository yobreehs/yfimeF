package io.github.chsbuffer.revancedxposed.youtube.layout

import android.widget.TextView
import app.revanced.extension.youtube.patches.NavigationButtonsPatch
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.NavigationBarHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.misc.hookNavigationButtonCreated
import org.luckypray.dexkit.wrap.DexMethod

fun YoutubeHook.NavigationButtons() {
    dependsOn(::NavigationBarHook)

    val preferences = mutableSetOf(
        SwitchPreference("revanced_hide_home_button"),
        SwitchPreference("revanced_hide_shorts_button"),
        SwitchPreference("revanced_hide_create_button"),
        SwitchPreference("revanced_hide_subscriptions_button"),
        SwitchPreference("revanced_hide_notifications_button"),
        SwitchPreference("revanced_switch_create_with_notifications_button"),
        SwitchPreference("revanced_hide_navigation_button_labels"),
    )

    PreferenceScreen.GENERAL_LAYOUT.addPreferences(
        PreferenceScreenPreference(
            key = "revanced_navigation_buttons_screen",
            sorting = Sorting.UNSORTED,
            preferences = preferences
        )
    )

    // Hide navigation button labels.
    getDexMethod("createPivotBarFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
            returns("V")
            parameters(
                "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;",
                "Landroid/widget/TextView;",
                "Ljava/lang/CharSequence;",
            )
            opcodes(
                Opcode.INVOKE_VIRTUAL,
                Opcode.RETURN_VOID,
            )
        }
    }.hookMethod(ScopedHook(DexMethod("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V").toMethod()) {
        before {
            NavigationButtonsPatch.hideNavigationButtonLabels(param.thisObject as TextView)
        }
    })

    // Switch create with notifications button.
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
    }.hookMethod(ScopedHook(getDexMethod("AutoMotiveFeatureMethod").toMethod()) {
        before {
            param.result = NavigationButtonsPatch.switchCreateWithNotificationButton()
        }
    })

    hookNavigationButtonCreated.add { button, view -> NavigationButtonsPatch.navigationTabCreated(button, view) }
}