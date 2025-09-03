package io.github.chsbuffer.revancedxposed.reddit.ad.comments

import io.github.chsbuffer.revancedxposed.fingerprint

val hideCommentAdsFingerprint = fingerprint {
    strings(
        "link",
        // CommentPageRepository is not returning a link object
        "is not returning a link object"
    )
}