package io.github.chsbuffer.revancedxposed.youtube.misc.playertype

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Fingerprint
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.resourceMappings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.FieldUsingType

val playerTypeFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    methodMatcher {
        addParamType { superClass { descriptor = "Ljava/lang/Enum;" } }
    }
    classMatcher {
        className(".YouTubePlayerOverlaysLayout", StringMatchType.EndsWith)
    }
}

val reelWatchPlayerId = resourceMappings["id", "reel_watch_player"]
val reelWatchPagerFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Landroid/view/View;")
    literal { reelWatchPlayerId }
}

val ReelPlayerViewField = findFieldDirect {
    reelWatchPagerFingerprint().declaredClass!!.fields.single { it.typeName.endsWith("ReelPlayerView") }
}

val ControlsState = findClassDirect {
    findClass {
        matcher {
            usingStrings("controls can be in the buffering state only if in PLAYING or PAUSED video state")
        }
    }.single()
}

val videoStateFingerprint = findMethodDirect {
    val controlsStateClass = ControlsState(this).descriptor
    val methodMatcher = Fingerprint(this) {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        returns("V")
        parameters(controlsStateClass)
        opcodes(
            Opcode.CONST_4,
            Opcode.IF_EQZ,
            Opcode.IF_EQZ,
            Opcode.IGET_OBJECT, // obfuscated parameter field name
        )
    }.methodMatcher

    findMethod { matcher(methodMatcher) }.first()
}

val videoStateParameterField = findFieldDirect {
    videoStateFingerprint().let { method ->
        method.usingFields.distinct().single { field ->
            // obfuscated parameter field name
            field.usingType == FieldUsingType.Read && field.field.declaredClass == method.paramTypes[0]
        }.field
    }
}