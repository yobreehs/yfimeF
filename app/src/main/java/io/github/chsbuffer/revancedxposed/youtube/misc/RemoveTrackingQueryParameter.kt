package io.github.chsbuffer.revancedxposed.youtube.misc

import android.content.ClipData
import android.content.Intent
import app.revanced.extension.youtube.settings.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.SwitchPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

fun YoutubeHook.RemoveTrackingQueryParameter() {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("revanced_remove_tracking_query_parameter"),
    )

    val sanitizeArg1 = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!Settings.REMOVE_TRACKING_QUERY_PARAMETER.get()) return
            val url = param.args[1] as String
            param.args[1] = url.replace(".si=.+".toRegex(), "").replace(".feature=.+".toRegex(), "")
        }
    }

    XposedHelpers.findAndHookMethod(
        ClipData::class.java.name,
        lpparam.classLoader,
        "newPlainText",
        CharSequence::class.java,
        CharSequence::class.java,
        sanitizeArg1
    )

    XposedHelpers.findAndHookMethod(
        Intent::class.java.name,
        lpparam.classLoader,
        "putExtra",
        String::class.java,
        String::class.java,
        sanitizeArg1
    )
}
