package io.github.chsbuffer.revancedxposed.strava.subscription

import io.github.chsbuffer.revancedxposed.strava.StravaHook

fun StravaHook.UnlockSubscription() {
    ::getSubscribedFingerprint.hookMethod {
        before { param ->
            param.result = true
        }
    }
}