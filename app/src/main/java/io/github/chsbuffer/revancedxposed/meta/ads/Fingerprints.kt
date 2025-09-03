package io.github.chsbuffer.revancedxposed.meta.ads

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.strings
import java.lang.reflect.Modifier

val adInjectorFingerprint = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.PRIVATE
            returnType = "void"
            strings(
                "SponsoredContentController.processValidatedContent",
            )
        }
    }.single()
}