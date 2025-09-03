package io.github.chsbuffer.revancedxposed.reddit.misc.tracking.url

import io.github.chsbuffer.revancedxposed.findMethodDirect

val shareLinkFormatterFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "java.lang.String"
            paramTypes("java.lang.String", null)
            usingEqStrings(
                "url",
                "getQueryParameterNames(...)",
                "getQueryParameters(...)",
                "toString(...)"
            )
        }
    }.single()
}