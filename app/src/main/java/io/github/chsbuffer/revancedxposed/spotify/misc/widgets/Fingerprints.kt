package io.github.chsbuffer.revancedxposed.spotify.misc.widgets

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint

/**
 * The method that checks whether the calling package holds the BIND_APPWIDGET permission.
 *
 * Only system app launchers are granted that permission, so Spotify uses this check to deny
 * its media provider to third party launchers (breaking the widgets with launchers like Nova).
 * The method has a stable shape across recent app versions:
 * `com.spotify.externalintegration.service.provider.MediaProvider.a(Context):Z`.
 *
 * The primary fingerprint pins the class (a manifest-declared ContentProvider, so its name is
 * not obfuscated) plus the signature. If a future version renames the method, we fall back to
 * matching the permission string on any boolean method.
 */
val canBindAppWidgetPermissionFingerprint = findMethodDirect {
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