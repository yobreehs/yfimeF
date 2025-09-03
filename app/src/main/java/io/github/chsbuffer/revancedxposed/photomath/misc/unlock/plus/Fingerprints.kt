package io.github.chsbuffer.revancedxposed.photomath.misc.unlock.plus

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

val isPlusUnlockedFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Z")
    strings("genius")
    classMatcher { className(".User", StringMatchType.EndsWith) }
}