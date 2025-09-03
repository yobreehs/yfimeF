package io.github.chsbuffer.revancedxposed.youtube.ad.video

import io.github.chsbuffer.revancedxposed.fingerprint

val loadVideoAdsFingerprint = fingerprint {
    strings(
        "TriggerBundle doesn't have the required metadata specified by the trigger ",
//        "Tried to enter slot with no assigned slotAdapter",
//        "Trying to enter a slot when a slot of same type and physical position is already active. Its status: ",
    )
}