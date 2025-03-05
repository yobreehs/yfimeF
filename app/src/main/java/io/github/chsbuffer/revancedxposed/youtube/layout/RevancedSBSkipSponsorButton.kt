package io.github.chsbuffer.revancedxposed.youtube.layout

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.revanced.extension.shared.Utils
import io.github.chsbuffer.revancedxposed.R
import io.github.chsbuffer.revancedxposed.new
import io.github.chsbuffer.revancedxposed.youtube.modRes

class RevancedSBSkipSponsorButton(context: Context, hostCl: ClassLoader) : LinearLayout(context) {

    val textView: TextView
    val imageView: ImageView

    init {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics)
                .toInt()
        )
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
                .toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
                .toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
                .toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
                .toInt()
        )

        textView =
            (hostCl.loadClass("com.google.android.libraries.youtube.common.ui.YouTubeTextView")
                .new(context, null) as TextView).apply {
                layoutParams =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                includeFontPadding = false
                setPadding(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
                    ).toInt(), 0, TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
                    ).toInt(), 0
                )
                isSingleLine = true
                text = modRes.getString(R.string.revanced_sb_skip_button_compact)
                setTextColor(0xFFFF_FFFF.toInt()) // White color
                textSize = 12f
            }
        addView(textView)

        imageView = ImageView(context).apply {
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            alpha = 0.8f
            contentDescription = null
            setPadding(
                0,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
                    .toInt(),
                0,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
                    .toInt()
            )

            setImageResource(Utils.getResourceIdentifier("quantum_ic_skip_next_white_24", "drawable"))
        }
        addView(imageView)
    }
}