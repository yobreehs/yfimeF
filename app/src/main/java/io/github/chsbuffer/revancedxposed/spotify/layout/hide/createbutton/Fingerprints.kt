package io.github.chsbuffer.revancedxposed.spotify.layout.hide.createbutton

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.SkipTest
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.fingerprint

@get:SkipTest
val oldNavigationBarAddItemFingerprint = fingerprint {
    strings("Bottom navigation tabs exceeds maximum of 5 tabs")
}

@get:SkipTest
val navigationBarItemSetClass = findClassDirect {
    fingerprint {
        strings("NavigationBarItemSet(")
    }.declaredClass!!
}

@get:SkipTest
val navigationBarItemSetConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    // Make sure the method checks whether navigation bar items are null before adding them.
    // If this is not true, then we cannot patch the method and potentially transform the parameters into null.
    opcodes(Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL)
    classMatcher { className(navigationBarItemSetClass(dexkit).name) }
    methodMatcher { addInvoke { name = "add" } }
}
