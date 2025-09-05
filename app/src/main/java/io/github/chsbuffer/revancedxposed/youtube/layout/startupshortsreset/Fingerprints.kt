package io.github.chsbuffer.revancedxposed.youtube.layout.startupshortsreset

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint

val userWasInShortsFingerprint = fingerprint {
    returns("V")
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    parameters("Ljava/lang/Object;")
    strings("userIsInShorts: ")
}

val userWasInShortsBuilderFingerprint = findMethodDirect {
    userWasInShortsFingerprint().invokes.findMethod {
        matcher { paramTypes("boolean", "int") }
    }.single()
}

/**
 * 18.15.40+
 */
val userWasInShortsConfigFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Z")
    literal {
        45358360L
    }
}
