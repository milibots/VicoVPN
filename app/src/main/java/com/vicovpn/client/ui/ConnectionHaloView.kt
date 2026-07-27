package com.vicovpn.client.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.vicovpn.client.R
import kotlin.math.min

class ConnectionHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private var phase = 0f
    private var connected = false
    private var working = false
    private var error = false
    private var traffic = 0f

    private val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                phase = it.animatedFraction
                invalidate()
            }
        }

    fun setState(
        connected: Boolean,
        working: Boolean,
        error: Boolean
    ) {
        this.connected = connected
        this.working = working
        this.error = error
        invalidate()
    }

    fun setTraffic(
        uploadBytesPerSecond: Long,
        downloadBytesPerSecond: Long
    ) {
        traffic =
            (
                (
                    uploadBytesPerSecond +
                        downloadBytesPerSecond
                    ).coerceAtLeast(0L)
                    .toDouble() /
                    2_500_000.0
                )
                .coerceIn(0.0, 1.0)
                .toFloat()

        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (
            ValueAnimator.areAnimatorsEnabled() &&
            !animator.isStarted
        ) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        if (width <= 0f || height <= 0f) {
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val size = min(width, height)

        val accent =
            ContextCompat.getColor(
                context,
                if (error) {
                    R.color.vico_premium_error
                } else {
                    R.color.vico_premium_orange
                }
            )

        val night =
            resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val activity =
            when {
                connected -> 1f
                working -> 0.78f
                error -> 0.9f
                else -> 0.42f
            }

        val breathing = 0.97f + phase * 0.05f
        val innerRadius = size * 0.205f
        val trafficBoost = traffic * 0.08f

        drawShadow(
            canvas,
            centerX,
            centerY + size * 0.018f,
            innerRadius * 1.22f,
            night
        )

        drawGlow(
            canvas,
            centerX,
            centerY,
            size * (0.26f + trafficBoost) * breathing,
            accent,
            (58f * activity).toInt()
        )

        drawRing(
            canvas,
            centerX,
            centerY,
            size * 0.285f * breathing,
            accent,
            (84f * activity).toInt(),
            size * 0.008f
        )

        drawRing(
            canvas,
            centerX,
            centerY,
            size * 0.365f * (0.985f + phase * 0.035f),
            accent,
            (48f * activity).toInt(),
            size * 0.006f
        )

        drawRing(
            canvas,
            centerX,
            centerY,
            size * 0.445f * (0.99f + phase * 0.025f),
            accent,
            (28f * activity).toInt(),
            size * 0.004f
        )

        drawHighlight(
            canvas,
            centerX,
            centerY,
            innerRadius * 1.06f,
            night
        )
    }

    private fun drawGlow(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
        alpha: Int
    ) {
        fillPaint.shader =
            RadialGradient(
                x,
                y,
                radius,
                intArrayOf(
                    Color.argb(
                        alpha.coerceIn(0, 255),
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    ),
                    Color.argb(
                        (alpha * 0.28f).toInt().coerceIn(0, 255),
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(x, y, radius, fillPaint)
        fillPaint.shader = null
    }

    private fun drawRing(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
        alpha: Int,
        stroke: Float
    ) {
        ringPaint.color = color
        ringPaint.alpha = alpha.coerceIn(0, 255)
        ringPaint.strokeWidth = stroke
        canvas.drawCircle(x, y, radius, ringPaint)
    }

    private fun drawShadow(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        night: Boolean
    ) {
        val alpha = if (night) 155 else 48

        fillPaint.shader =
            RadialGradient(
                x,
                y,
                radius,
                intArrayOf(
                    Color.argb(alpha, 0, 0, 0),
                    Color.argb(alpha / 3, 0, 0, 0),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.48f, 0.76f, 1f),
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(x, y, radius, fillPaint)
        fillPaint.shader = null
    }

    private fun drawHighlight(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        night: Boolean
    ) {
        ringPaint.color = Color.WHITE
        ringPaint.alpha = if (night) 42 else 100
        ringPaint.strokeWidth = radius * 0.025f

        canvas.drawArc(
            x - radius,
            y - radius,
            x + radius,
            y + radius,
            205f,
            130f,
            false,
            ringPaint
        )
    }
}
