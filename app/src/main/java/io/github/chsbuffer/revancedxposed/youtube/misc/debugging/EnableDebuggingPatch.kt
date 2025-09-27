package io.github.chsbuffer.revancedxposed.youtube.misc.debugging

import io.github.chsbuffer.revancedxposed.shared.misc.debugging.EnableDebugging
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.settings.PreferenceScreen

fun YoutubeHook.EnableDebugging() {
    EnableDebugging(
        preferenceScreen = PreferenceScreen.MISC,
        additionalDebugPreferences = listOf(SwitchPreference("revanced_debug_protobuffer"))
    )
}
