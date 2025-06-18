package io.github.chsbuffer.revancedxposed

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.util.DexSignUtil.getTypeName
import java.lang.reflect.Modifier

fun DexKitBridge.fingerprint(block: MethodMatcher.() -> Unit): MethodData {
    return findMethod {
        matcher {
            block(this)
        }
    }.single()
}

fun MethodMatcher.strings(vararg strings: String) {
    this.usingStrings(strings.toList())
}

fun MethodMatcher.opcodes(vararg opcodes: Opcode): OpCodesMatcher {
    return OpCodesMatcher(opcodes.map { it.opCode }).also {
        this.opCodes(it)
    }
}

enum class AccessFlags(val modifier: Int) {
    PUBLIC(Modifier.PUBLIC),
    PRIVATE(Modifier.PRIVATE),
    PROTECTED(Modifier.PROTECTED),
    STATIC(Modifier.STATIC),
    FINAL(Modifier.FINAL),
    CONSTRUCTOR(0),
}

fun MethodMatcher.accessFlags(vararg accessFlags: AccessFlags) {
    this.modifiers(accessFlags.map { it.modifier }.reduce { acc, next -> acc or next })
    if (accessFlags.contains(AccessFlags.CONSTRUCTOR)) {
        if (accessFlags.contains(AccessFlags.STATIC))
            this.name = "<clinit>"
        else
            this.name = "<init>"
    }
}

fun MethodMatcher.parameters(vararg parameters: String) {
    this.paramTypes(parameters.map {
        if (it.trimStart('[').startsWith('L') && !it.endsWith(';')) null
        else getTypeName(it)
    })
}

fun MethodMatcher.returns(returnType: String) {
    this.returnType = getTypeName(returnType)
}

fun MethodMatcher.definingClass(definingClass: String) {
    this.declaredClass = getTypeName(definingClass)
}

fun MethodMatcher.literal(literalSupplier: () -> Number) {
    this.usingNumbers(literalSupplier())
}
