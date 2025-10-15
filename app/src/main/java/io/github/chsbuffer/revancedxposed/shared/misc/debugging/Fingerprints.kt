package io.github.chsbuffer.revancedxposed.shared.misc.debugging

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.TargetApp
import io.github.chsbuffer.revancedxposed.accessFlags
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.parameters
import io.github.chsbuffer.revancedxposed.returns
import io.github.chsbuffer.revancedxposed.strings

internal val experimentalFeatureFlagParentFingerprint = findClassDirect {
    findMethod {
        matcher {
            accessFlags(AccessFlags.STATIC)
            returns("L")
            parameters("L", "J", "[B")
            strings("Unable to parse proto typed experiment flag: ")
        }
    }.map { it.declaredClass }.distinct().single()!!
}

internal val experimentalBooleanFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).name) }
    accessFlags(AccessFlags.STATIC)
    returns("Z")
    parameters("L", "J", "Z")
}

internal val experimentalDoubleFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).name) }
    accessFlags(AccessFlags.STATIC)
    returns("D")
    parameters("L", "J", "D")
}

internal val experimentalLongFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).name) }
    accessFlags(AccessFlags.STATIC)
    returns("J")
    parameters("L", "J", "J")
}

@get:TargetApp("youtube")
internal val experimentalStringFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).name) }
    accessFlags(AccessFlags.STATIC)
    returns("Ljava/lang/String;")
    parameters("L", "J", "Ljava/lang/String;")
}
