@file:Suppress("DEPRECATION")

package io.github.chsbuffer.revancedxposed.shared.misc.settings.preference

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceManager
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference.*

open class PreferenceCategory(
    key: String? = null,
    titleKey: String? = "${key}_title",
    icon: String? = null,
    layout: String? = null,
    sorting: Sorting = Sorting.BY_TITLE,
    tag: String = "PreferenceCategory",
    val preferences: Set<BasePreference>
) : BasePreference(sorting.appendSortType(key), titleKey, null, icon, layout, tag) {
    override fun build(ctx: Context, prefMgr: PreferenceManager): Preference {
        return PreferenceCategory(ctx).apply {
            applyBaseAttrs(this)
            preferences.forEach { pref -> addPreference(pref.build(ctx, prefMgr)) }
        }
    }
}
