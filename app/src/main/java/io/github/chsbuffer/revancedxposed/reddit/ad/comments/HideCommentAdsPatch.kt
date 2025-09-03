package io.github.chsbuffer.revancedxposed.reddit.ad.comments

import app.revanced.extension.shared.Logger
import io.github.chsbuffer.revancedxposed.reddit.RedditHook

fun RedditHook.HideCommentAds() {
    ::hideCommentAdsFingerprint.hookMethod {
        before {
            Logger.printDebug { "Hide Comment" }
            it.result = Object()
        }
    }
}