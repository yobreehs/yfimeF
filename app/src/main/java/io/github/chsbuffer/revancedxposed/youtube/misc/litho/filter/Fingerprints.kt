package io.github.chsbuffer.revancedxposed.youtube.misc.litho.filter

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.RequireAppVersion
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.youtube.shared.conversionContextFingerprintToString
import org.luckypray.dexkit.result.FieldUsingType

//val componentContextParserFingerprint = fingerprint {
//    strings("Number of bits must be positive")
//}

val componentCreateFingerprint = fingerprint {
    strings(
        "Element missing correct type extension",
        "Element missing type"
    )
}

val protobufBufferReferenceFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    parameters("I", "Ljava/nio/ByteBuffer;")
    opcodes(
        Opcode.IPUT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.SUB_INT_2ADDR,
    )
}

val emptyComponentFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE, AccessFlags.CONSTRUCTOR)
    parameters()
    strings("EmptyComponent")
}

val lithoThreadExecutorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    parameters("I", "I", "I")
    classMatcher {
        superClass {
            descriptor = "Ljava/util/concurrent/ThreadPoolExecutor;"
        }
    }
    literal { 1L }
}

@get:RequireAppVersion("19.25.00", "20.04.99")
val lithoComponentNameUpbFeatureFlagFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Z")
    parameters()
    literal { 45631264L }
}

val lithoConverterBufferUpbFeatureFlagFingerprint = fingerprint {
//    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
//    returns("L")
//    parameters("L")
    literal { 45419603L }
}

//region rvxp
val conversionContextClass = findClassDirect {
    conversionContextFingerprintToString(this).declaredClass!!
}
val identifierFieldData = findFieldDirect {
    val stringFieldIndex =
        if (findMethod { matcher { usingStrings(", pathInternal=") } }.any()) 2 else 1
    conversionContextClass(this).methods.single {
        it.isConstructor && it.paramCount != 0
    }.usingFields.filter {
        it.usingType == FieldUsingType.Write && it.field.typeName == String::class.java.name
    }[stringFieldIndex].field
}

val pathBuilderFieldData = findFieldDirect {
    conversionContextClass(this).fields.single { it.typeSign == "Ljava/lang/StringBuilder;" }
}

val emptyComponentClass = findClassDirect {
    emptyComponentFingerprint().declaredClass!!
}

val featureFlagCheck = findMethodDirect {
    lithoConverterBufferUpbFeatureFlagFingerprint().invokes.findMethod {
        matcher {
            returnType = "boolean"
            paramTypes("long", "boolean")
        }
    }.single()
}
//endregion
