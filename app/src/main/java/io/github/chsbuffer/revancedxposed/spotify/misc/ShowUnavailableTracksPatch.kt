package io.github.chsbuffer.revancedxposed.spotify.misc

import app.revanced.extension.shared.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

/**
 * Forces Spotify's own "Show unplayable songs" setting on.
 *
 * Spotify already ships this setting; when disabled the app hides tracks that cannot be played
 * (wrong market, removed, …) from collections instead of showing them greyed out.
 *
 * Two implementations are needed because the setting store changed:
 *
 * - up to 9.1.92 the setting lives in the reactive `com.spotify.settings.rxsettings.SettingsState`
 *   class under `show_unavailable_tracks`, so every freshly built state has that boolean field set;
 * - since 9.1.94 the store is the protobuf message `…esperanto.proto.SettingsOuterClass$SettingsState`
 *   with the field `showUnavailableTracks_`, and the app reads it through the generated getter. The
 *   protobuf parser writes the fields directly after construction, so writing the field from a
 *   constructor hook would be overwritten — the getter is hooked instead and always returns `true`.
 */
fun SpotifyHook.ShowUnavailableTracks() {
    runCatching {
        val getter = ::showUnavailableTracksProtoGetter.method
        XposedBridge.hookMethod(getter, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })
        Logger.printInfo {
            "ShowUnavailableTracks: forced ${getter.declaringClass.simpleName}.${getter.name}() = true"
        }
    }.recoverCatching {
        val field = ::showUnavailableTracksField.field
        var writeFailureReported = false
        XposedBridge.hookAllConstructors(field.declaringClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    XposedHelpers.setBooleanField(param.thisObject, field.name, true)
                }.onFailure { err ->
                    // Report once: this runs on every settings state construction.
                    if (!writeFailureReported) {
                        writeFailureReported = true
                        Logger.printException(
                            { "ShowUnavailableTracks: cannot set ${field.declaringClass.simpleName}.${field.name}" },
                            err
                        )
                    }
                }
            }
        })
        Logger.printInfo { "ShowUnavailableTracks: forced ${field.declaringClass.simpleName}.${field.name} = true" }
    }.onFailure { err ->
        Logger.printException({ "ShowUnavailableTracks: hook failed" }, err)
    }
}
