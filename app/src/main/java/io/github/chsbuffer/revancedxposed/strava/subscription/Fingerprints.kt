package io.github.chsbuffer.revancedxposed.strava.subscription

import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val getSubscribedFingerprint = fingerprint {
    opcodes(Opcode.IGET_BOOLEAN)
    classMatcher { className(".SubscriptionDetailResponse", StringMatchType.EndsWith) }
    methodMatcher { name = "getSubscribed" }
}