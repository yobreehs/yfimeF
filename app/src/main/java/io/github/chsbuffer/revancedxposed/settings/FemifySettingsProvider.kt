package io.github.chsbuffer.revancedxposed.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log

/**
 * Read-only bridge that lets the Spotify process read the module settings.
 *
 * The module used to share its preferences through `XSharedPreferences`. LSPosed declares that
 * mechanism deprecated ("New XSharedPreferences", slated for removal in v2.3.0), so the settings
 * are now private to the module app and are returned from here over Binder instead.
 *
 * The provider must be exported because the caller is a different app. Every call is checked
 * against the caller UID (our own app or [SPOTIFY_PACKAGE]) or, when PackageManager answers
 * nothing because the module is hidden from Spotify, against the shared
 * [ModuleSettings.CALL_TOKEN] passed as the `arg` of `call()`. Neither check depends on the
 * provider being resolvable, so the token keeps the bridge working under HMA-style hiding of the
 * *reverse* direction; the forward direction (Spotify resolving this provider at all) still needs
 * the module to be visible to Spotify.
 */
class FemifySettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_GET) return null

        val callingUid = Binder.getCallingUid()
        // `arg` carries the shared token, which survives package-visibility filtering when a
        // package-hiding module (HMA) hides this module from the Spotify process.
        if (arg != ModuleSettings.CALL_TOKEN && !isTrustedCaller(callingUid)) {
            Log.w(TAG, "Rejected settings read from uid $callingUid")
            return null
        }

        val context = context ?: return null
        val prefs = context.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
        return Bundle().apply {
            ModuleSettings.defaults.forEach { (key, default) ->
                putBoolean(key, prefs.getBoolean(key, default))
            }
        }
    }

    private fun isTrustedCaller(uid: Int): Boolean {
        if (uid == Process.myUid()) return true

        val packageManager = context?.packageManager ?: return false
        val packages = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull()
        if (packages?.any { it == SPOTIFY_PACKAGE } == true) return true

        // Package visibility can hide getPackagesForUid() results on Android 11+; the manifest
        // <queries> entry makes this fallback work as well.
        return runCatching {
            packageManager.getApplicationInfo(SPOTIFY_PACKAGE, 0).uid == uid
        }.getOrDefault(false)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        /** Manifest authority is `${applicationId}.settings`. */
        const val AUTHORITY_SUFFIX = ".settings"

        const val METHOD_GET = "get"
        const val SPOTIFY_PACKAGE = "com.spotify.music"

        private const val TAG = "FemifySettings"
    }
}
