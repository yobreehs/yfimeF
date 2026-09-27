package io.github.chsbuffer.revancedxposed.spotify.misc.privacy

import android.content.ClipData
import android.content.Intent
import app.revanced.extension.shared.Logger
import app.revanced.extension.spotify.misc.privacy.SanitizeSharingLinksPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

fun SpotifyHook.SanitizeSharingLinks() {
    // 1. Sanitize every Spotify share link written to the clipboard.
    //
    // Spotify copies share links through several code paths (the share-sheet coroutine and
    // a separate "copy link" effect handler), and these paths change between app versions.
    // Hooking ClipData.newPlainText globally covers all of them and never depends on a
    // fingerprint staying valid. Only URLs actually get sanitized, plain text is untouched.
    XposedHelpers.findAndHookMethod(
        ClipData::class.java,
        "newPlainText",
        CharSequence::class.java,
        CharSequence::class.java,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args[1] as? CharSequence ?: return
                param.args[1] = SanitizeSharingLinksPatch.sanitizeSharingLink(url.toString())
            }
        })

    // 2. Sanitize links shared through the Android share sheet (Intent.EXTRA_TEXT).
    XposedHelpers.findAndHookMethod(
        Intent::class.java,
        "putExtra",
        String::class.java,
        String::class.java,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == Intent.EXTRA_TEXT) {
                    val url = param.args[1] as String
                    param.args[1] = SanitizeSharingLinksPatch.sanitizeSharingLink(url)
                }
            }
        })

    // 3. Keep the direct copy-link hook as a fallback. It is intentionally isolated:
    //    a fingerprint mismatch on a future app version must not take the hooks above down.
    runCatching {
        ::shareCopyUrlFingerprint.hookMethod(
            scopedHook(
                XposedHelpers.findMethodExact(
                    ClipData::class.java.name,
                    lpparam.classLoader,
                    "newPlainText",
                    CharSequence::class.java,
                    CharSequence::class.java
                )
            ) {
                before { param ->
                    val url = param.args[1] as? CharSequence ?: return@before
                    param.args[1] = SanitizeSharingLinksPatch.sanitizeSharingLink(url.toString())
                }
            })
    }.onFailure { err ->
        Logger.printException({ "SanitizeSharingLinks: shareCopyUrlFingerprint fallback failed (non-fatal)" }, err)
    }
}