package io.github.chsbuffer.revancedxposed.strava.upselling

import io.github.chsbuffer.revancedxposed.getObjectFieldOrNullAs
import io.github.chsbuffer.revancedxposed.strava.StravaHook
import java.util.Collections

fun StravaHook.DisableSubscriptionSuggestions() {
    ::getModulesFingerprint.hookMethod {
        before { param ->
            val pageValue = param.thisObject.getObjectFieldOrNullAs<String>("page") ?: return@before
            if (pageValue.contains("_upsell") || pageValue.contains("promo")) {
                param.result = Collections.EMPTY_LIST
            }
        }
    }
}