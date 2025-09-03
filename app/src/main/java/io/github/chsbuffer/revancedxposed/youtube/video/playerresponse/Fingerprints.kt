package io.github.chsbuffer.revancedxposed.youtube.video.playerresponse

import io.github.chsbuffer.revancedxposed.findMethodDirect

internal val playerParameterBuilderFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingStrings("psns", "psnr", "psps", "pspe")
        }
    }.single {
        it.paramTypeNames.contains("java.lang.String")
    }
}