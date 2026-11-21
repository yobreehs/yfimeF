package io.github.chsbuffer.revancedxposed.settings

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import io.github.chsbuffer.revancedxposed.BuildConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * User-facing switches of the module.
 *
 * They are edited in [SettingsActivity], which runs in the module app. The Spotify hooks run in
 * another process, so the module app exposes the values through [FemifySettingsProvider] and this
 * object reads them synchronously through `ContentResolver.call()`. Preferences themselves are
 * ordinary `MODE_PRIVATE` storage, so no world-readable files are needed.
 *
 * Values are read once per Spotify start; changes apply after Spotify is restarted.
 */
object ModuleSettings {
    const val PREFS_NAME = "femify_settings"

    const val PREMIUM_UNLOCK = "premium_unlock"
    const val BLOCK_ADS = "block_ads"
    const val SHOW_UNAVAILABLE_TRACKS = "show_unavailable_tracks"
    const val SANITIZE_SHARING_LINKS = "sanitize_sharing_links"

    /**
     * Experimental override for the ad-blocking region dialog patch. When off (default), the
     * "listening abroad" dialog is hidden together with the ads. Turn it on to see the dialog
     * again without disabling the whole ad blocker.
     */
    const val FORCE_SHOW_REGION_DIALOG = "force_show_region_dialog"

    /** Experimental features, hidden until the version footer is tapped eight times. */
    const val EXPERIMENTAL_UNLOCKED = "experimental_unlocked"
    const val EXPERIMENTAL_INITIALIZED = "experimental_initialized"
    const val REGION_PROXY_ENABLED = "region_proxy_enabled"
    const val REGION_PROXY_COUNTRY = "region_proxy_country"
    const val REGION_PROXY_CUSTOM = "region_proxy_custom"

    /**
     * Snapshot of the values Spotify actually loaded at process start, written by
     * [FemifySettingsProvider]. The settings screen compares the live preferences with it to tell
     * the user whether a restart is still needed.
     */
    const val APPLIED_SNAPSHOT = "applied_snapshot"

    const val TAG = "FemifySettings"

    /**
     * Passed as the `arg` of `ContentResolver.call()` and required by [FemifySettingsProvider] as a
     * second factor next to the caller UID. The UID check needs PackageManager, which answers
     * "nothing" when the module is hidden from Spotify by a package-hiding Xposed module (HMA) —
     * in that case the token is the only thing that still proves the caller is the hooked Spotify
     * process rather than a random app.
     */
    const val CALL_TOKEN = "femify-settings-v1"

    private val settingsUri: Uri =
        Uri.parse("content://${BuildConfig.APPLICATION_ID}${FemifySettingsProvider.AUTHORITY_SUFFIX}")

    /** Boolean switches. Order is also the order of the switches in [SettingsActivity]. */
    val booleanDefaults: Map<String, Boolean> = linkedMapOf(
        PREMIUM_UNLOCK to true,
        BLOCK_ADS to true,
        SHOW_UNAVAILABLE_TRACKS to true,
        SANITIZE_SHARING_LINKS to true,
        FORCE_SHOW_REGION_DIALOG to false,
        REGION_PROXY_ENABLED to false,
    )

    /** String settings (currently the experimental region proxy). */
    val stringDefaults: Map<String, String> = linkedMapOf(
        REGION_PROXY_COUNTRY to "NL",
        REGION_PROXY_CUSTOM to "",
    )

    /** Combined defaults used when neither settings transport answers. */
    val defaults: Map<String, Any> = booleanDefaults + stringDefaults

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cached: Map<String, Any>? = null

    @Volatile
    private var failureLogged = false

    /**
     * Must be called from the hooked Spotify process before the hooks are selected. The Spotify
     * `Application` is already available at that point.
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
        val loaded = load()
        cached = loaded
        // Tell the settings screen which values this Spotify process is really using. It is the
        // basis for the "all patches applied" / "restart required" status line.
        loaded?.let(::reportApplied)
    }

    fun isEnabled(key: String): Boolean =
        (snapshot()[key] as? Boolean) ?: booleanDefaults[key] ?: false

    fun getString(key: String): String =
        (snapshot()[key] as? String) ?: stringDefaults[key] ?: ""

    fun reload() {
        cached = load()
    }

    private fun snapshot(): Map<String, Any> {
        cached?.let { return it }
        val loaded = load() ?: return defaults
        cached = loaded
        return loaded
    }

    private fun load(): Map<String, Any>? =
        loadFromBroadcast() ?: loadFromProvider() ?: run {
            if (!failureLogged) {
                failureLogged = true
                Log.w(TAG, "No settings transport answered, defaults are used")
            }
            null
        }

    /**
     * Persist the values this process started with in the module app. Best effort: the provider can
     * be unreachable when the module is hidden from Spotify, in which case the settings screen
     * simply assumes the current preferences are the applied ones.
     */
    private fun reportApplied(settings: Map<String, Any>) {
        val context = appContext ?: return
        val extras = Bundle().apply {
            booleanDefaults.keys.forEach { key ->
                (settings[key] as? Boolean)?.let { putBoolean(key, it) }
            }
            stringDefaults.keys.forEach { key ->
                (settings[key] as? String)?.let { putString(key, it) }
            }
        }
        val providerOk = runCatching {
            context.contentResolver.call(
                settingsUri, FemifySettingsProvider.METHOD_APPLIED, CALL_TOKEN, extras
            )
        }.isSuccess

        if (!providerOk) {
            // The same package-visibility problem that forces the settings read onto the broadcast
            // path also hides the provider from Spotify. The receiver can always be reached.
            runCatching {
                context.sendBroadcast(
                    Intent(FemifySettingsReceiver.ACTION_SET_APPLIED)
                        .setPackage(BuildConfig.APPLICATION_ID)
                        .putExtras(extras)
                        .putExtra(FemifySettingsReceiver.EXTRA_TOKEN, CALL_TOKEN)
                )
            }.onFailure {
                Log.d(TAG, "Could not report the applied settings snapshot", it)
            }
        }
    }

    /**
     * Preferred transport: an ordered broadcast answered by [FemifySettingsReceiver] in the module
     * app. Unlike the provider this does not need Spotify's UID to *see* the module package, so it
     * also survives package-hiding modules.
     *
     * The result callback is delivered on the thread of the scheduler handler, hence the dedicated
     * [HandlerThread]: without it the answer would be posted to the main looper, which is exactly
     * the thread that is blocked here waiting for it.
     */
    private fun loadFromBroadcast(): Map<String, Any>? {
        val context = appContext ?: return null

        val scheduler = HandlerThread("femify-settings").apply { start() }
        try {
            val latch = CountDownLatch(1)
            var extras: Bundle? = null

            val resultReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    extras = runCatching { getResultExtras(false) }.getOrNull()
                    latch.countDown()
                }
            }

            val intent = Intent(FemifySettingsReceiver.ACTION_GET_SETTINGS)
                .setPackage(BuildConfig.APPLICATION_ID)

            context.sendOrderedBroadcast(
                intent,
                null,
                resultReceiver,
                Handler(scheduler.looper),
                Activity.RESULT_CANCELED,
                null,
                null,
            )

            if (!latch.await(2, TimeUnit.SECONDS)) {
                Log.d(TAG, "The broadcast settings request timed out, falling back to the provider")
                return null
            }

            return extras?.let(::toMap)
        } catch (error: Exception) {
            Log.d(TAG, "The broadcast settings request failed, falling back to the provider", error)
            return null
        } finally {
            scheduler.quitSafely()
        }
    }

    private fun loadFromProvider(): Map<String, Any>? {
        val context = appContext ?: return null
        val bundle = runCatching {
            // The token is the second authentication factor: the caller UID check inside the
            // provider relies on PackageManager, which is filtered when "Hide My Applist" hides
            // this module from Spotify (or on Android 11+ without package visibility).
            context.contentResolver.call(
                settingsUri, FemifySettingsProvider.METHOD_GET, CALL_TOKEN, null
            )
        }.onFailure { error ->
            if (!failureLogged) {
                failureLogged = true
                // A resolution failure here means Spotify's UID cannot see the module package:
                // either Android 11+ package visibility (android:forceQueryable) or a
                // package-hiding module such as HMA ("Hide My Applist").
                Log.w(
                    TAG, "Cannot read settings from the Femify provider " +
                            "$settingsUri, the switches are ignored and defaults are used", error
                )
            }
        }.getOrNull()

        if (bundle == null) {
            if (!failureLogged) {
                failureLogged = true
                Log.w(TAG, "The Femify provider returned no settings, defaults are used")
            }
            return null
        }

        return toMap(bundle)
    }

    private fun toMap(bundle: Bundle): Map<String, Any> = buildMap {
        booleanDefaults.forEach { (key, default) ->
            put(key, bundle.getBoolean(key, default))
        }
        stringDefaults.forEach { (key, default) ->
            put(key, bundle.getString(key) ?: default)
        }
    }
}
