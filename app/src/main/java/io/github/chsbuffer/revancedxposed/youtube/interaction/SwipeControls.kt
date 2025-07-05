package io.github.chsbuffer.revancedxposed.youtube.interaction

import app.revanced.extension.shared.settings.preference.ColorPickerPreference
import app.revanced.extension.youtube.swipecontrols.SwipeControlsHostActivity
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.InputType
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.ListPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.TextPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PlayerTypeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import org.luckypray.dexkit.query.enums.StringMatchType

fun YoutubeHook.SwipeControls() {

    PreferenceScreen.SWIPE_CONTROLS.addPreferences(
        SwitchPreference("revanced_swipe_brightness"),
        SwitchPreference("revanced_swipe_volume"),
        SwitchPreference("revanced_swipe_press_to_engage"),
        SwitchPreference("revanced_swipe_haptic_feedback"),
        SwitchPreference("revanced_swipe_save_and_restore_brightness"),
        SwitchPreference("revanced_swipe_lowest_value_enable_auto_brightness"),
        ListPreference("revanced_swipe_overlay_style"),
        TextPreference("revanced_swipe_overlay_background_opacity", inputType = InputType.NUMBER),
        TextPreference("revanced_swipe_overlay_progress_brightness_color",
            tag = ColorPickerPreference::class.java,
            inputType = InputType.TEXT_CAP_CHARACTERS),
        TextPreference("revanced_swipe_overlay_progress_volume_color",
            tag = ColorPickerPreference::class.java,
            inputType = InputType.TEXT_CAP_CHARACTERS),
        TextPreference("revanced_swipe_text_overlay_size", inputType = InputType.NUMBER),
        TextPreference("revanced_swipe_overlay_timeout", inputType = InputType.NUMBER),
        TextPreference("revanced_swipe_threshold", inputType = InputType.NUMBER),
        TextPreference("revanced_swipe_volume_sensitivity", inputType = InputType.NUMBER),
    )

    dependsOn(
        ::PlayerTypeHook,
    )

    val mainActivityClass = getDexClass("mainActivityFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
            parameters()
            classMatcher {
                className(".MainActivity", StringMatchType.EndsWith)
            }
        }.declaredClass!!
    }.toClass()

    SwipeControlsHostActivity.hookActivity(mainActivityClass)
}