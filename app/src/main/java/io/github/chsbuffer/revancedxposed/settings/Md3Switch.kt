package io.github.chsbuffer.revancedxposed.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import android.widget.CompoundButton

/**
 * Material 3 style switch (52x32 dp track, 24 dp handle, 2 dp outline when off).
 *
 * The Material Components library is deliberately not a dependency of this module: it would add
 * several megabytes of resources to an APK that has to be loaded by the Spotify process. This view
 * draws the switch itself and only needs the four MD3 colours, so it follows the system palette on
 * Android 12+ just like the library does.
 *
 * It extends [CompoundButton] so that toggling, accessibility (role "switch", checked state) and
 * the click sound keep working.
 */
class Md3Switch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CompoundButton(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val pressed = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var animator: ValueAnimator? = null

    /** 0 = off, 1 = on; animated between the two. */
    private var fraction = 0f

    var trackColorOn: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var trackColorOff: Int = Color.LTGRAY
        set(value) {
            field = value
            invalidate()
        }

    var thumbColorOn: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var thumbColorOff: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    var outlineColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    init {
        setButtonDrawable(null)
        background = null
        isClickable = true
        isFocusable = true
    }

    override fun setChecked(checked: Boolean) {
        if (isChecked == checked) return
        super.setChecked(checked)
        animateTo(if (checked) 1f else 0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // First layout: do not animate, just show the stored value.
        fraction = if (isChecked) 1f else 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 52x48 dp: the track is 32 dp tall, the extra height is the touch target.
        setMeasuredDimension(dp(52f).toInt(), dp(48f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val trackWidth = dp(52f)
        val trackHeight = dp(32f)
        val left = (width - trackWidth) / 2f
        val top = (height - trackHeight) / 2f
        val radius = trackHeight / 2f
        track.set(left, top, left + trackWidth, top + trackHeight)

        // Track.
        paint.style = Paint.Style.FILL
        paint.color = blend(trackColorOff, trackColorOn, fraction)
        canvas.drawRoundRect(track, radius, radius, paint)

        // 2 dp outline while the switch is (mostly) off.
        val outlineAlpha = (1f - fraction * 2f).coerceIn(0f, 1f)
        if (outlineAlpha > 0f) {
            val stroke = dp(2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = withAlpha(outlineColor, outlineAlpha)
            val inset = stroke / 2f
            track.inset(inset, inset)
            canvas.drawRoundRect(track, radius - inset, radius - inset, paint)
            track.inset(-inset, -inset)
        }

        // Handle.
        val handleRadius = dp(12f)
        val startX = left + dp(4f) + handleRadius
        val endX = left + trackWidth - dp(4f) - handleRadius
        paint.style = Paint.Style.FILL
        paint.color = blend(thumbColorOff, thumbColorOn, fraction)
        canvas.drawCircle(startX + (endX - startX) * fraction, height / 2f, handleRadius, paint)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (!isAttachedToWindow) {
            fraction = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 200
            interpolator = pressed
            addUpdateListener {
                fraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun blend(from: Int, to: Int, t: Float): Int = Color.argb(
        (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
