package io.github.chsbuffer.revancedxposed.youtube.misc

import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

fun YoutubeHook.FixVerticalScroll() {
    getDexMethod("canChildScrollUpFingerprint") {
        findMethod {
            matcher {
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                returnType = "boolean"
                paramCount = 0
                opcodes(
                    Opcode.MOVE_RESULT,
                    Opcode.RETURN,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT
                )
                declaredClass(".SwipeRefreshLayout", StringMatchType.EndsWith)
            }
        }.single()
    }.hookMethod(XC_MethodReplacement.returnConstant(false))
}