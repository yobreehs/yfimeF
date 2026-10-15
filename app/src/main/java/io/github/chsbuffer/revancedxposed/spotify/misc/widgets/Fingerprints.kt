package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint

/**
 * The permission/caller check that decides whether a media-provider caller (e.g. a launcher
 * host) may read Spotify's widget artwork.
 *
 * Spotify's own logic is (as of 9.1.90, inlined into `MediaProvider.openFile`):
 * `trustedCaller(context) || (widgetProvidersFlag && callerHoldsBIND_APPWIDGET)`, where
 * `trustedCaller` validates the caller's signing certificate against
 * `res/xml/allowed_media_browser_callers*.xml` (internally logged as `MBS:`). Only system
 * components pass, so third-party launchers are denied.
 *
 * Up to ~9.1.86 this was a standalone helper `MediaProvider.a(Context):Z`; the patch replaced
 * it with `true`. Since ~9.1.90 that helper is inlined, so the reusable gate is the
 * media-browser caller validator itself. Fingerprinted by its unique log messages, so it
 * survives class/method obfuscation, with the legacy `MediaProvider.a(Context):Z` matcher as a
 * fallback for older builds.
 */
val mbsCallerValidationFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Z")
            strings(
                "MBS: Caller has more than one signature certificate!",
                "MBS: Package manager can't find package: %s"
            )
        }
    }.getOrElse {
        runCatching {
            fingerprint {
                classMatcher { descriptor = "Lcom/spotify/externalintegration/service/provider/MediaProvider;" }
                returns("Z")
                parameters("Landroid/content/Context;")
                strings("android.permission.BIND_APPWIDGET")
            }
        }.getOrElse {
            fingerprint {
                returns("Z")
                strings("android.permission.BIND_APPWIDGET")
            }
        }
    }
}
