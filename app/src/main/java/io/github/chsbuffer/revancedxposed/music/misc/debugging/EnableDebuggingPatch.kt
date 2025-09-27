package io.github.chsbuffer.revancedxposed.music.misc.debugging

import io.github.chsbuffer.revancedxposed.shared.misc.debugging.EnableDebugging
import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.music.misc.settings.PreferenceScreen

fun MusicHook.EnableDebugging() {
    EnableDebugging(
        preferenceScreen = PreferenceScreen.MISC
    )
}
