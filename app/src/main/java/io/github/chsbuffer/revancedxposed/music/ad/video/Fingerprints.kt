package io.github.chsbuffer.revancedxposed.music.ad.video

import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint

val showVideoAdsParentFingerprint = fingerprint {
    opcodes(
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.IGET_OBJECT,
    )
    strings("maybeRegenerateCpnAndStatsClient called unexpectedly, but no error.")
}

val showVideoAds = findMethodDirect {
    showVideoAdsParentFingerprint().invokes.findMethod {
        matcher {
            paramTypes("boolean")
        }
    }.single()
}