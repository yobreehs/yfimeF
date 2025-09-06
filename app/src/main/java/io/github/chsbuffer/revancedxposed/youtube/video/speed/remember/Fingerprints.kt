package io.github.chsbuffer.revancedxposed.youtube.video.speed.remember

import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.fingerprint

internal val initializePlaybackSpeedValuesFingerprint = fingerprint {
    parameters("[L", "I")
    strings("menu_item_playback_speed")
}

val onItemClickListenerClassFieldReference = findFieldDirect {
    initializePlaybackSpeedValuesFingerprint().usingFields.first().field
}