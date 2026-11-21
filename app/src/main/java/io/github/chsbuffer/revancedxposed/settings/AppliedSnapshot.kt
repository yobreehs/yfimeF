package io.github.chsbuffer.revancedxposed.settings

import android.content.Context
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

/**
 * Stores the settings snapshot that the Spotify process reported at startup.
 *
 * The provider is the primary transport, but package visibility can hide it from Spotify (the same
 * reason [ModuleSettings] falls back to an ordered broadcast for reading). The receiver calls this
 * object too, so the status line works even when the provider cannot be resolved.
 */
internal object AppliedSnapshot {
    private const val TAG = "FemifySettings"

    fun store(context: Context, extras: Bundle?) {
        if (extras == null) return
        val snapshot = JSONObject()
        ModuleSettings.booleanDefaults.keys.forEach { key ->
            if (extras.containsKey(key)) snapshot.put(key, extras.getBoolean(key))
        }
        ModuleSettings.stringDefaults.keys.forEach { key ->
            if (extras.containsKey(key)) snapshot.put(key, extras.getString(key).orEmpty())
        }
        context.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ModuleSettings.APPLIED_SNAPSHOT, snapshot.toString())
            .apply()
        Log.d(TAG, "Stored applied settings snapshot with ${snapshot.length()} entries")
    }
}
