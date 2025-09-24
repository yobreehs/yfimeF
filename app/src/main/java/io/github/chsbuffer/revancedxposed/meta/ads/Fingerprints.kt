package io.github.chsbuffer.revancedxposed.meta.ads

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.strings

val adInjectorFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            strings(
                "SponsoredContentController.processValidatedContent",
            )
        }
    }.single()
}