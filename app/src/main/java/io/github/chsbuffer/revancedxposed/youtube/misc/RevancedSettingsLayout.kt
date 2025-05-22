@file:Suppress("DEPRECATION")

package io.github.chsbuffer.revancedxposed.youtube.misc

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toolbar
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.ThemeHelper
import app.revanced.extension.youtube.settings.preference.ReVancedPreferenceFragment
import io.github.chsbuffer.revancedxposed.BuildConfig


// revanced_settings_with_toolbar.xml
class RevancedSettingsLayout(activity: Activity) : LinearLayout(activity) {
    private val toolbarParent: FrameLayout
    val toolbar: Toolbar
    val fragmentsContainer: FrameLayout

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        fitsSystemWindows = true
        orientation = VERTICAL
        isTransitionGroup = true

        // instead of placeholder toolbar,
        // this is youtube settings patch toolbar.
        toolbarParent = FrameLayout(activity).apply {
            id = generateViewId()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(Utils.getResourceColor("yt_white1"))
            elevation = 0f
        }
        addView(toolbarParent)

        toolbar = Toolbar(activity)
        toolbar.setBackgroundColor(ThemeHelper.getToolbarBackgroundColor())
        toolbar.navigationIcon = ReVancedPreferenceFragment.getBackButtonDrawable()
        toolbar.setNavigationOnClickListener { view: View -> activity.onBackPressed() }

        val margin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16f,
            Utils.getContext().resources.displayMetrics
        ).toInt()
        toolbar.titleMarginStart = margin
        toolbar.titleMarginEnd = margin
        val toolbarTextView = Utils.getChildView<TextView>(
            toolbar, false
        ) { view: View? -> view is TextView }
        toolbarTextView?.setTextColor(ThemeHelper.getForegroundColor())
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(
            Utils.getResourceIdentifier("actionBarSize", "attr"),
            typedValue,
            true
        )
        val actionBarSize = TypedValue.complexToDimensionPixelSize(
            typedValue.data,
            activity.resources.displayMetrics
        )
        toolbar.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            actionBarSize
        )

        toolbarParent.addView(toolbar)

        fragmentsContainer = FrameLayout(activity).apply {
            id = generateViewId() // Generate a unique ID
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(fragmentsContainer)
    }

    fun setTitle(resourceName: String) {
        toolbar.title = context.getString(
            context.resources.getIdentifier(
                resourceName,
                "string",
                BuildConfig.APPLICATION_ID
            )
        )
    }
}