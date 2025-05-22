package io.github.chsbuffer.revancedxposed.youtube.layout

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.RelativeLayout
import app.revanced.extension.youtube.sponsorblock.ui.SkipSponsorButton
import io.github.chsbuffer.revancedxposed.R

// revanced_sb_inline_sponsor_overlay.xml
class RevancedSBInlineSponsorOverlay(context: Context) : RelativeLayout(context) {

    val skipHighlightButton: SkipSponsorButton
    val skipSponsorButton: SkipSponsorButton
//    val newSegmentLayout: NewSegmentLayout

    init {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)

        skipHighlightButton = SkipSponsorButton(context).apply {
            id = generateViewId()
            contentDescription = context.getString(R.string.revanced_sb_skip_button_compact_highlight)
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                addRule(ALIGN_PARENT_LEFT)
                addRule(ALIGN_PARENT_BOTTOM)
                bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics).toInt()
            }
            isFocusable = true
            visibility = GONE
        }
        addView(skipHighlightButton)

        skipSponsorButton = SkipSponsorButton(context).apply {
            id = generateViewId()
            contentDescription = context.getString(R.string.revanced_sb_skip_button_compact)
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                addRule(ALIGN_PARENT_RIGHT)
                addRule(ALIGN_PARENT_BOTTOM)
                bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics).toInt()
            }
            isFocusable = true
            visibility = GONE
        }
        addView(skipSponsorButton)

/*        newSegmentLayout = NewSegmentLayout(context).apply {
            id = generateViewId()
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                addRule(ALIGN_PARENT_LEFT)
                addRule(ALIGN_PARENT_BOTTOM)
                bottomMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44f, resources.displayMetrics).toInt()
            }
            isFocusable = true
            visibility = GONE
        }
        addView(newSegmentLayout)*/
    }

}