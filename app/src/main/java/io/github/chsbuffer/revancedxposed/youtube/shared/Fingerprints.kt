package io.github.chsbuffer.revancedxposed.youtube.shared

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

internal const val YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/google/android/apps/youtube/app/watchwhile/MainActivity;"

val conversionContextFingerprintToString = fingerprint {
    parameters()
    strings("ConversionContext{")
}

val mainActivityConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    parameters()
    classMatcher {
        className(".MainActivity", StringMatchType.EndsWith)
    }
}

val mainActivityClass = findClassDirect {
    mainActivityConstructorFingerprint().declaredClass!!
}

val mainActivityOnBackPressedFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    parameters()
    methodMatcher { name = "onBackPressed" }
    classMatcher { className(".MainActivity", StringMatchType.EndsWith) }
}

val mainActivityOnCreateFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/os/Bundle;")
    methodMatcher { name = "onCreate" }
    classMatcher { className(".MainActivity", StringMatchType.EndsWith) }
}

val seekbarFingerprint = fingerprint {
    returns("V")
    strings("timed_markers_width")
}

val videoQualityChangedFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    methodMatcher {
        addInvoke {
            declaredClass =
                "com.google.android.libraries.youtube.innertube.model.media.VideoQuality"
            name = "<init>"
        }
        addUsingField {
            field {
                // VIDEO_QUALITY_SETTING_UNKNOWN Enum
                declaredClass { usingStrings("VIDEO_QUALITY_SETTING_UNKNOWN") }
                modifiers = Modifier.STATIC
                name = "a"
            }
        }
    }
}

val VideoQualityReceiver = findMethodDirect {
    videoQualityChangedFingerprint().invokes.single { it.paramCount == 1 && it.paramTypeNames[0] == "com.google.android.libraries.youtube.innertube.model.media.VideoQuality" }
}