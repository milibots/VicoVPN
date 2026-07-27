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
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.vicovpn.client.R
import kotlin.math.cos
import kotlin.math.sin

class AmbientSmokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private var phase = 0f

    private val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 26_000L
            repeatCount =
                ValueAnimator.INFINITE
            interpolator =
                LinearInterpolator()
            addUpdateListener {
                phase = it.animatedFraction
                invalidate()
            }
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

        val night =
            resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        canvas.drawColor(
            ContextCompat.getColor(
                context,
                R.color.vico_premium_background
            )
        )

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val angle =
            phase *
                Math.PI.toFloat() *
                2f

        if (!night) {
            /*
             * Light mode is intentionally pure white. The dark animated
             * atmosphere must not bleed into the light theme.
             */
            return
        }

        drawSmoke(
            canvas,
            width * (0.28f + 0.08f * sin(angle)),
            height * (0.19f + 0.05f * cos(angle * 0.7f)),
            width * 0.70f,
            Color.argb(55, 135, 135, 142)
        )

        drawSmoke(
            canvas,
            width * (0.80f + 0.06f * cos(angle * 0.8f)),
            height * (0.38f + 0.07f * sin(angle * 0.55f)),
            width * 0.58f,
            Color.argb(34, 105, 105, 112)
        )

        drawSmoke(
            canvas,
            width * (0.50f + 0.08f * sin(angle * 0.45f)),
            height * 0.90f,
            width * 0.72f,
            Color.argb(30, 255, 82, 42)
        )
    }

    private fun drawSmoke(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        centerColor: Int
    ) {
        paint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                intArrayOf(
                    centerColor,
                    Color.argb(
                        8,
                        Color.red(centerColor),
                        Color.green(centerColor),
                        Color.blue(centerColor)
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            paint
        )
        paint.shader = null
    }
}
