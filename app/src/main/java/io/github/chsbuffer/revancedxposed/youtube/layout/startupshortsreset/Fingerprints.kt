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
    val paramTypes = listOf("boolean", "int")
    userWasInShortsFingerprint().invokes.single {
        it.paramTypeNames.takeLast(2) == paramTypes
    }
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
