package io.github.chsbuffer.revancedxposed.settings

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Second transport for the settings bridge, next to [FemifySettingsProvider].
 *
 * The provider has to be *resolved* by the Spotify process, and that lookup is filtered by package
 * visibility (Android 11+) and by package-hiding modules such as HMA ("Hide My Applist"). Both
 * filter the caller, not the receiver here: an ordered broadcast is delivered by the system and the
 * answer comes back through the result extras, so this path keeps working even when Spotify's UID
 * cannot see this package at all.
 */
class FemifySettingsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_GET_SETTINGS -> {
                val prefs = context.getSharedPreferences(
                    ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE
                )
                val bundle = Bundle().apply {
                    ModuleSettings.booleanDefaults.forEach { (key, default) ->
                        putBoolean(key, prefs.getBoolean(key, default))
                    }
                    ModuleSettings.stringDefaults.forEach { (key, default) ->
                        putString(key, prefs.getString(key, default) ?: default)
                    }
                }

                Log.d(TAG, "Serving ${bundle.size()} settings over broadcast")
                resultCode = Activity.RESULT_OK
                setResultExtras(bundle)
            }

            ACTION_SET_APPLIED -> {
                if (intent.getStringExtra(EXTRA_TOKEN) != ModuleSettings.CALL_TOKEN) return
                AppliedSnapshot.store(context, intent.extras)
            }
        }
    }

    companion object {
        const val ACTION_GET_SETTINGS = "io.github.chsbuffer.revancedxposed.action.GET_SETTINGS"
        const val ACTION_SET_APPLIED = "io.github.chsbuffer.revancedxposed.action.SET_APPLIED"
        const val EXTRA_TOKEN = "token"

        private const val TAG = "FemifySettings"
    }
}
