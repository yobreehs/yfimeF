package io.github.chsbuffer.revancedxposed.strava.upselling

import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val getModulesFingerprint = fingerprint {
    opcodes(Opcode.IGET_OBJECT)
    methodMatcher { name = "getModules" }
    classMatcher { className(".GenericLayoutEntry", StringMatchType.EndsWith) }
}
