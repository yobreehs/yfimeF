package io.github.chsbuffer.revancedxposed.youtube.misc

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout

class RevancedSettingsLayout(context: Context) : LinearLayout(context) {

    val fragmentsContainer: FrameLayout

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        fitsSystemWindows = true
        isTransitionGroup = true

        fragmentsContainer = FrameLayout(context).apply {
            id = generateViewId() // Generate a unique ID
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(fragmentsContainer)
    }
}