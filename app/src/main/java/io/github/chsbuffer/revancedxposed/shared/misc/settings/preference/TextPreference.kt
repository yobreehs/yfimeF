package io.github.chsbuffer.revancedxposed.shared.misc.settings.preference

import android.preference.EditTextPreference
import app.revanced.extension.shared.settings.preference.ResettableEditTextPreference

class TextPreference(
    key: String? = null,
    titleKey: String = "${key}_title",
    summaryKey: String? = "${key}_summary",
    icon: String? = null,
    layout: String? = null,
    tag: Class<out EditTextPreference> = ResettableEditTextPreference::class.java,
    val inputType: InputType = InputType.TEXT
) : BasePreference(key, titleKey, summaryKey, icon, layout, tag) {
}
