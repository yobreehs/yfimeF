package io.github.chsbuffer.revancedxposed.music.layout.premium

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.fingerprint

val hideGetPremiumFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    strings("FEmusic_history", "FEmusic_offline")
}