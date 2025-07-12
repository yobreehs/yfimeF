package io.github.chsbuffer.revancedxposed.spotify

import android.content.ClipData
import app.revanced.extension.spotify.misc.privacy.SanitizeSharingLinksPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.fingerprint
import java.lang.reflect.Modifier

fun SpotifyHook.SanitizeSharingLinks() {
    getDexMethod("shareCopyUrlFingerprint") {
        runCatching {
            fingerprint {
                returns("Ljava/lang/Object;")
                parameters("Ljava/lang/Object;")
                strings("clipboard", "Spotify Link")
                methodMatcher { name = "invokeSuspend" }
            }
        }.getOrElse {
            fingerprint {
                returns("Ljava/lang/Object;")
                parameters("Ljava/lang/Object;")
                strings("clipboard", "createNewSession failed")
                methodMatcher { name = "apply" }
            }
        }
    }.hookMethod(
        ScopedHook(
            XposedHelpers.findMethodExact(
                ClipData::class.java.name,
                lpparam.classLoader,
                "newPlainText",
                CharSequence::class.java,
                CharSequence::class.java
            )
        ) {
            before {
                val url = param.args[1] as String
                param.args[1] = SanitizeSharingLinksPatch.sanitizeUrl(url)
            }
        }
    )

    getDexMethod("formatAndroidShareSheetUrlFingerprint") {
        runCatching {
            findMethod {
                matcher {
                    returnType("java.lang.String")
                    addUsingNumber('\n'.code)
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramTypes(null, "java.lang.String")
                }
            }.single {
                // exclude
                // `(PlayerState, String) -> String` usingNumbers(1, 10); usingStrings("")
                !it.usingStrings.contains("")
            }
        }.getOrElse {
            findMethod {
                matcher {
                    returnType("java.lang.String")
                    addUsingNumber('\n'.code)
                    modifiers = Modifier.PUBLIC
                    paramTypes("com.spotify.share.social.sharedata.ShareData", "java.lang.String")
                }
            }.single {
                // exclude
                // `(PlayerState, String) -> String` usingNumbers(1, 10); usingStrings("")
                !it.usingStrings.contains("")
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val url = param.args[1] as String
            param.args[1] = SanitizeSharingLinksPatch.sanitizeUrl(url)
        }
    })
}