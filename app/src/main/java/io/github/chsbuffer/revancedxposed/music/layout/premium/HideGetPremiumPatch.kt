package io.github.chsbuffer.revancedxposed.music.layout.premium

import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import io.github.chsbuffer.revancedxposed.music.MusicHook

fun MusicHook.HideGetPremium() {
    ::hideGetPremiumFingerprint.hookMethod {
        val id = Utils.getResourceIdentifier("unlimited_panel", "id")
        after { param ->
            val thiz = param.thisObject
            for (field in thiz.javaClass.fields) {
                val view = field.get(thiz)
                if (view !is View) continue
                val panelView = view.findViewById<View>(id) ?: continue
                Logger.printDebug { "hide get premium" }
                panelView.visibility = View.GONE
                break
            }
        }
    }
}