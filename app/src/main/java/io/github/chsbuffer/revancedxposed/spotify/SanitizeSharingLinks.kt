package io.github.chsbuffer.revancedxposed.spotify

import android.content.ClipData
import app.revanced.extension.spotify.misc.privacy.SanitizeSharingLinksPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.strings
import java.lang.reflect.Modifier

fun SpotifyHook.SanitizeSharingLinks() {
    getDexMethod("shareCopyUrlFingerprint") {
        findMethod {
            matcher {
                if (IS_SPOTIFY_LEGACY_APP_TARGET) {
                    returnType("java.lang.Object")
                    paramTypes("java.lang.Object")
                    strings("clipboard", "createNewSession failed")
                    name("apply")
                } else {
                    returnType("java.lang.Object")
                    paramTypes("java.lang.Object")
                    strings("clipboard", "Spotify Link")
                    name("invokeSuspend")
                }
            }
        }.single()
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
            before { param, _ ->
                val url = param.args[1] as String
                param.args[1] = SanitizeSharingLinksPatch.sanitizeUrl(url)
            }
        }
    )

    getDexMethod("formatAndroidShareSheetUrlFingerprint") {
        findMethod {
            matcher {
                returnType("java.lang.String")
                addUsingNumber('\n'.code)
                if (IS_SPOTIFY_LEGACY_APP_TARGET) {
                    modifiers = Modifier.PUBLIC
                    paramTypes("com.spotify.share.social.sharedata.ShareData", "java.lang.String")
                } else {
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramTypes(null, "java.lang.String")
                }
            }
        }.single()
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val url = param.args[1] as String
            param.args[1] = SanitizeSharingLinksPatch.sanitizeUrl(url)
        }
    })
}