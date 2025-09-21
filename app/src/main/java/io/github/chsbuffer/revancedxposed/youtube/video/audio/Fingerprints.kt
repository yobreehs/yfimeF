package io.github.chsbuffer.revancedxposed.youtube.video.audio

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.RequireAppVersion
import io.github.chsbuffer.revancedxposed.accessFlags
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.returns

internal val formatStreamModelToStringFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Ljava/lang/String;")
    name("toString")
    definingClass("Lcom/google/android/libraries/youtube/innertube/model/media/FormatStreamModel;")
}

val getFormatFingerprint = findMethodDirect {
    findMethod {
        matcher {
            returnType = "androidx.media3.common.Format"
            declaredClass = "com.google.android.libraries.youtube.innertube.model.media.FormatStreamModel"
            paramCount = 0
        }
    }.single()
}

val getIsDefaultAudioTrackFingerprint = findMethodDirect {
    formatStreamModelToStringFingerprint().invokes.findMethod {
        matcher {
            addCaller(getFormatFingerprint().descriptor)
            returns("Z")
            opcodes(
                Opcode.IGET_OBJECT,
                Opcode.IGET_OBJECT,
                Opcode.IF_NEZ,
                Opcode.SGET_OBJECT,
                Opcode.IGET_BOOLEAN,
            )
        }
    }.single()
}

val getAudioTrackIdFingerprint = findMethodDirect {
    val toStringInvokes = formatStreamModelToStringFingerprint().invokes
    val isDefaultAudioTrack = getIsDefaultAudioTrackFingerprint()
    toStringInvokes[toStringInvokes.indexOf(isDefaultAudioTrack) + 1].also {
        require(it.returnTypeName == "java.lang.String")
    }
}

val getAudioTrackDisplayNameFingerprint = findMethodDirect {
    val toStringInvokes = formatStreamModelToStringFingerprint().invokes
    val isDefaultAudioTrack = getIsDefaultAudioTrackFingerprint()
    toStringInvokes[toStringInvokes.indexOf(isDefaultAudioTrack) + 2].also {
        require(it.returnTypeName == "java.lang.String")
    }
}

internal const val AUDIO_STREAM_IGNORE_DEFAULT_FEATURE_FLAG = 45666189L

@get:RequireAppVersion("20.07")
internal val selectAudioStreamFingerprint = findMethodDirect {
    findMethod {
        matcher {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            returns("L")
            addUsingNumber(AUDIO_STREAM_IGNORE_DEFAULT_FEATURE_FLAG)
        }
    }.single {
        it.paramCount > 2 &&
                it.paramTypes[1].descriptor == "Lcom/google/android/libraries/youtube/innertube/model/media/PlayerConfigModel;"
    }
}

