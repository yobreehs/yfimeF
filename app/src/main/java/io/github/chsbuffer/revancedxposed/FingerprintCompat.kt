package io.github.chsbuffer.revancedxposed

import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher


fun MethodMatcher.strings(vararg strings: String) {
    this.usingStrings(strings.toList())
}

fun MethodMatcher.opcodes(vararg opcodes: Opcode): OpCodesMatcher {
    return OpCodesMatcher(opcodes.map { it.opCode }).also {
            this.opCodes(it)
        }
}
