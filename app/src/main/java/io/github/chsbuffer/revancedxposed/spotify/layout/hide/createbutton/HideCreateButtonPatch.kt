package io.github.chsbuffer.revancedxposed.spotify.layout.hide.createbutton

import app.revanced.extension.spotify.layout.hide.createbutton.HideCreateButtonPatch
import io.github.chsbuffer.revancedxposed.hookMethod
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

fun SpotifyHook.HideCreateButton() {
    // Main patch for newest and most versions.
    // The NavigationBarItemSet constructor accepts multiple parameters which represent each navigation bar item.
    // Each item is manually checked whether it is not null and then added to a LinkedHashSet.
    // Since the order of the items can differ, we are required to check every parameter to see whether it is the
    // Create button. So, for every parameter passed to the method, invoke our extension method and overwrite it
    // to null in case it is the Create button.
    ::navigationBarItemSetConstructorFingerprint.memberOrNull?.hookMethod {
        before { param ->
            for ((i, arg) in param.args.withIndex()) {
                param.args[i] = HideCreateButtonPatch.returnNullIfIsCreateButton(arg)
            }
        }
    }

    ::oldNavigationBarAddItemFingerprint.memberOrNull?.hookMethod {
        before { param ->
            for (arg in param.args) {
                if (arg !is Int) continue
                if (HideCreateButtonPatch.isOldCreateButton(arg)) {
                    param.result = null
                    return@before
                }
            }
        }
    }
}