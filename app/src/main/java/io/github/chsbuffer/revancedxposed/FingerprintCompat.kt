package io.github.chsbuffer.revancedxposed

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.util.DexSignUtil.getTypeName
import java.lang.reflect.Modifier

private fun getTypeNameCompat(it: String): String? {
    return if (it.trimStart('[').startsWith('L') && !it.endsWith(';')) null
    else getTypeName(it)
}

class Fingerprint(val dexkit: DexKitBridge, init: Fingerprint.() -> Unit) {
    var classMatcher: ClassMatcher? = null
    val methodMatcher = MethodMatcher()

    init {
        init(this)
    }

    fun strings(vararg strings: String) {
        methodMatcher.usingStrings(strings.toList())
    }

    fun opcodes(vararg opcodes: Opcode): OpCodesMatcher {
        return OpCodesMatcher(opcodes.map { it.opCode }).also {
            methodMatcher.opCodes(it)
        }
    }

    fun accessFlags(vararg accessFlags: AccessFlags) {
        methodMatcher.modifiers(accessFlags.map { it.modifier }.reduce { acc, next -> acc or next })
        if (accessFlags.contains(AccessFlags.CONSTRUCTOR)) {
            if (accessFlags.contains(AccessFlags.STATIC)) methodMatcher.name = "<clinit>"
            else methodMatcher.name = "<init>"
        }
    }

    fun parameters(vararg parameters: String) {
        methodMatcher.paramTypes(parameters.map(::getTypeNameCompat))
    }

    fun returns(returnType: String) {
        getTypeNameCompat(returnType)?.let { methodMatcher.returnType = it }
    }

    fun literal(literalSupplier: () -> Number) {
        methodMatcher.usingNumbers(literalSupplier())
    }

    /*
    * dexkit method matcher
    * */
    fun methodMatcher(block: MethodMatcher.() -> Unit) {
        block(methodMatcher)
    }

    /*
    * dexkit class matcher
    * */
    fun classMatcher(block: ClassMatcher.() -> Unit) {
        classMatcher = ClassMatcher().apply(block)
    }

    fun run(): MethodData {
        return if (classMatcher != null) {
            dexkit.findClass {
                matcher(classMatcher!!)
            }.findMethod {
                matcher(methodMatcher)
            }.single()
        } else {
            dexkit.findMethod {
                matcher(methodMatcher)
            }.single()
        }
    }
}

fun DexKitBridge.fingerprint(block: Fingerprint.() -> Unit): MethodData {
    return Fingerprint(this, block).run()
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
        if (accessFlags.contains(AccessFlags.STATIC)) this.name = "<clinit>"
        else this.name = "<init>"
    }
}

fun MethodMatcher.parameters(vararg parameters: String) {
    this.paramTypes(parameters.map(::getTypeNameCompat))
}

fun MethodMatcher.returns(returnType: String) {
    getTypeNameCompat(returnType)?.let { this.returnType = it }
}

fun MethodMatcher.literal(literalSupplier: () -> Number) {
    this.usingNumbers(literalSupplier())
}
