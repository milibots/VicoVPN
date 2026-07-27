package com.vicovpn.client.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.math.sin

class ConnectedSuccessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val circlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f *
                resources.displayMetrics.density
        }

    private val checkPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 6f *
                resources.displayMetrics.density
            color = Color.WHITE
        }

    private val checkPath = Path()

    private var phase = 0f

    private val animator =
        ValueAnimator.ofFloat(
            0f,
            1f
        ).apply {
            duration = 900L
            interpolator =
                DecelerateInterpolator()

            addUpdateListener {
                phase =
                    it.animatedValue as Float
                invalidate()
            }

            addListener(
                object :
                    AnimatorListenerAdapter() {
                    override fun onAnimationEnd(
                        animation: Animator
                    ) {
                        animate()
                            .alpha(0f)
                            .setDuration(260L)
                            .withEndAction {
                                visibility = GONE
                                alpha = 1f
                            }
                            .start()
                    }
                }
            )
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        setLayerType(
            LAYER_TYPE_SOFTWARE,
            null
        )
    }

    fun play() {
        animator.cancel()
        animate().cancel()

        visibility = VISIBLE
        alpha = 1f
        phase = 0f

        if (
            ValueAnimator
                .areAnimatorsEnabled()
        ) {
            animator.start()
        } else {
            phase = 1f
            invalidate()

            postDelayed(
                {
                    visibility = GONE
                },
                850L
            )
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius =
            min(
                width,
                height
            ) * 0.18f

        val entrance =
            phase.coerceIn(
                0f,
                1f
            )

        val pulse =
            sin(
                entrance *
                    Math.PI
                        .toFloat()
            )

        val radius =
            baseRadius *
                (
                    0.74f +
                        entrance *
                        0.26f
                    )

        val green =
            Color.rgb(
                34,
                197,
                94
            )

        circlePaint.color = green
        circlePaint.alpha =
            (
                230f *
                    entrance
                ).toInt()

        circlePaint.setShadowLayer(
            26f *
                resources.displayMetrics.density,
            0f,
            8f *
                resources.displayMetrics.density,
            Color.argb(
                150,
                34,
                197,
                94
            )
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            circlePaint
        )

        circlePaint.clearShadowLayer()

        repeat(2) { index ->
            ringPaint.color = green
            ringPaint.alpha =
                (
                    150f *
                        (1f - entrance) *
                        (1f - index * 0.25f)
                    ).toInt()
                        .coerceAtLeast(0)

            canvas.drawCircle(
                centerX,
                centerY,
                radius *
                    (
                        1.20f +
                            entrance *
                            (0.60f +
                                index *
                                0.24f) +
                            pulse *
                            0.06f
                        ),
                ringPaint
            )
        }

        val checkProgress =
            (
                (entrance - 0.22f) /
                    0.78f
                ).coerceIn(
                0f,
                1f
            )

        checkPath.reset()
        checkPath.moveTo(
            centerX -
                radius * 0.42f,
            centerY +
                radius * 0.02f
        )
        checkPath.lineTo(
            centerX -
                radius * 0.10f,
            centerY +
                radius * 0.34f
        )
        checkPath.lineTo(
            centerX +
                radius * 0.46f,
            centerY -
                radius * 0.32f
        )

        checkPaint.alpha =
            (
                255f *
                    checkProgress
                ).toInt()

        canvas.drawPath(
            checkPath,
            checkPaint
        )
    }
}
