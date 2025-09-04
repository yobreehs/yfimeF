package io.github.chsbuffer.revancedxposed.youtube.layout.buttons.navigation

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.strings

internal const val ANDROID_AUTOMOTIVE_STRING = "Android Automotive"

val addCreateButtonViewFingerprint = fingerprint {
    strings("Android Wear", ANDROID_AUTOMOTIVE_STRING)
}

// rvxp
val AutoMotiveFeatureMethod = findMethodDirect {
    addCreateButtonViewFingerprint().invokes.findMethod {
        matcher { strings("android.hardware.type.automotive") }
    }.single()
}

val createPivotBarFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    returns("V")
    parameters(
        "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;",
        "Landroid/widget/TextView;",
        "Ljava/lang/CharSequence;",
    )
    opcodes(
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID,
    )
}