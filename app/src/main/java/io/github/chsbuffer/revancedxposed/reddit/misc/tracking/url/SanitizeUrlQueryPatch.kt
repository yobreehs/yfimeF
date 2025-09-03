package io.github.chsbuffer.revancedxposed.reddit.misc.tracking.url

import io.github.chsbuffer.revancedxposed.reddit.RedditHook

fun RedditHook.SanitizeUrlQuery() {
    ::shareLinkFormatterFingerprint.hookMethod {
        before {
            it.result = it.args[0]
        }
    }
}