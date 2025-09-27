package io.github.chsbuffer.revancedxposed.shared.misc.debugging

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.TargetApp
import io.github.chsbuffer.revancedxposed.accessFlags
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.parameters
import io.github.chsbuffer.revancedxposed.returns
import io.github.chsbuffer.revancedxposed.strings

internal val experimentalFeatureFlagParentFingerprint = findMethodDirect {
    findMethod {
        matcher {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            returns("L")
            parameters("L", "J", "[B")
            strings("Unable to parse proto typed experiment flag: ")
        }
    }.first()
}

internal val experimentalBooleanFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).declaredClassName) }
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Z")
    parameters("L", "J", "Z")
}

internal val experimentalDoubleFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).declaredClassName) }
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("D")
    parameters("J", "D")
}

internal val experimentalLongFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).declaredClassName) }
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("J")
    parameters("J", "J")
}

@get:TargetApp("youtube")
internal val experimentalStringFeatureFlagFingerprint = fingerprint {
    classMatcher { className(experimentalFeatureFlagParentFingerprint(dexkit).declaredClassName) }
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Ljava/lang/String;")
    parameters("J", "Ljava/lang/String;")
}
