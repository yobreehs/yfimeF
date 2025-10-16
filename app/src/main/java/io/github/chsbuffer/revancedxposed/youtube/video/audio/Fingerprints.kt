package io.github.chsbuffer.revancedxposed.youtube.video.audio

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.findMethodListDirect
import io.github.chsbuffer.revancedxposed.fingerprint

internal val formatStreamModelToStringFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Ljava/lang/String;")
    name("toString")
    definingClass("Lcom/google/android/libraries/youtube/innertube/model/media/FormatStreamModel;")
}

/*
* isDefaultAudioTrack
* audioTrackId
* audioTrackDisplayName
* */
val getFormatStreamModelGetter = findMethodListDirect {
    formatStreamModelToStringFingerprint().invokes.windowed(3).first {
        it[0].returnTypeName == "boolean" &&
                it[1].returnTypeName == "java.lang.String" &&
                it[2].returnTypeName == "java.lang.String"
    }.also {
        it.forEach { m ->
            require(m.paramCount == 0) { "Expected no parameters for FormatStreamModel getter methods" }
            require(m.declaredClass!!.simpleName == "FormatStreamModel") { "Expected FormatStreamModel instance method" }
        }
    }
}

internal const val AUDIO_STREAM_IGNORE_DEFAULT_FEATURE_FLAG = 45666189L
