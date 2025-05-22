@file:Suppress("DEPRECATION", "DiscouragedApi")

package io.github.chsbuffer.revancedxposed.shared.misc.settings.preference

import android.content.Context
import android.content.res.Resources
import android.preference.Preference
import android.preference.PreferenceManager
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import io.github.chsbuffer.revancedxposed.BuildConfig
import io.github.chsbuffer.revancedxposed.R

@Suppress("MemberVisibilityCanBePrivate")
abstract class BasePreference(
    val key: String? = null,
    val titleKey: String? = "${key}_title",
    val summaryKey: String? = "${key}_summary",
    val icon: String? = null,
    val layout: String? = null,
    val tag: String
) {
    fun trySetString(
        key: String?,
        resources: Resources = Utils.getContext().resources,
        pkg: String = BuildConfig.APPLICATION_ID,
        setString: (str: String) -> Unit
    ) = trySetRes(key, "string", resources, pkg) { res, id -> setString(resources.getString(id)) }

    fun trySetRes(
        key: String?,
        type: String,
        resources: Resources = Utils.getContext().resources,
        pkg: String = BuildConfig.APPLICATION_ID,
        setRes: (res: Resources, id: Int) -> Unit,
    ) {
        if (key == null) return
        when (val id = resources.getIdentifier(key, type, pkg)) {
            0 -> Logger.printDebug { "$key not found." }
            else -> setRes(resources, id)
        }
    }

    fun applyBaseAttrs(preference: Preference) {
        preference.also { pref ->
            key?.let { pref.key = key }
            trySetString(titleKey) { pref.title = it }
            trySetString(summaryKey) { pref.summary = it }

            trySetRes(icon, "drawable") { res, id ->
                pref.icon = res.getDrawable(id, res.newTheme().apply {
                    applyStyle(R.style.MainTheme, true)
                })
            }
            layout?.let {
                pref.layoutResource = Utils.getResourceIdentifier(layout, "layout")
            }
        }
    }

    open fun build(ctx: Context, prefMgr: PreferenceManager): Preference {
        return Preference(ctx).apply { applyBaseAttrs(this) }
    }
}