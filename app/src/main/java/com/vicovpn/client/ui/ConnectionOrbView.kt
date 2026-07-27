package com.vicovpn.client.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

class ConnectionOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private val particlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private val wavePath = Path()

    private var state = State.DISCONNECTED
    private var phase = 0f
    private var trafficLevel = 0f

    private val lowPowerDevice =
        Runtime.getRuntime()
            .availableProcessors() <= 4

    private val mainParticleCount =
        if (lowPowerDevice) 250 else 520

    private val dustParticleCount =
        if (lowPowerDevice) 80 else 170

    private val animator =
        ValueAnimator.ofFloat(
            0f,
            PI.toFloat() * 2f
        ).apply {
            duration = 8_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

    fun setState(
        newState: State
    ) {
        if (state == newState) return
        state = newState
        invalidate()
    }

    fun setTrafficBytesPerSecond(
        upload: Long,
        download: Long
    ) {
        val combined =
            (upload + download)
                .coerceAtLeast(0L)

        trafficLevel =
            (combined.toDouble() /
                3_000_000.0)
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

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        if (width <= 0f || height <= 0f) {
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius =
            min(width, height) * 0.33f

        val profile = visualProfile()

        drawAmbientGlow(
            canvas,
            centerX,
            centerY,
            radius,
            profile
        )

        drawWaveLayers(
            canvas,
            centerX,
            centerY,
            radius,
            profile
        )

        drawMainParticleBand(
            canvas,
            centerX,
            centerY,
            radius,
            profile
        )

        drawFloatingDust(
            canvas,
            centerX,
            centerY,
            radius,
            profile
        )

        drawInnerHalo(
            canvas,
            centerX,
            centerY,
            radius,
            profile
        )
    }

    private fun drawAmbientGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        profile: VisualProfile
    ) {
        glowPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius * 1.34f,
                intArrayOf(
                    Color.argb(
                        profile.glowAlpha,
                        Color.red(profile.color),
                        Color.green(profile.color),
                        Color.blue(profile.color)
                    ),
                    Color.argb(
                        profile.glowAlpha / 3,
                        Color.red(profile.color),
                        Color.green(profile.color),
                        Color.blue(profile.color)
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(
                    0f,
                    0.54f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 1.34f,
            glowPaint
        )

        glowPaint.shader = null
    }

    private fun drawWaveLayers(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        profile: VisualProfile
    ) {
        val layers = 5
        val pointCount =
            if (lowPowerDevice) 170 else 280

        repeat(layers) { layer ->
            wavePath.reset()

            for (index in 0..pointCount) {
                val normalized =
                    index.toFloat() /
                        pointCount.toFloat()

                val angle =
                    normalized *
                        PI.toFloat() *
                        2f

                val layerPhase =
                    phase * profile.speed +
                        layer * 0.57f

                val wave =
                    sin(
                        angle * 3f +
                            layerPhase
                    ) *
                        profile.amplitude +
                        sin(
                            angle * 6f -
                                layerPhase * 0.72f
                        ) *
                        profile.amplitude *
                        0.47f +
                        sin(
                            angle * 11f +
                                layerPhase * 0.33f
                        ) *
                        profile.amplitude *
                        0.18f

                val trafficWave =
                    sin(
                        angle * 8f -
                            phase * 1.65f
                    ) *
                        radius *
                        0.045f *
                        trafficLevel

                val layerRadius =
                    radius *
                        (0.93f +
                            layer * 0.035f) +
                        wave +
                        trafficWave

                val x =
                    centerX +
                        cos(angle) *
                        layerRadius

                val y =
                    centerY +
                        sin(angle) *
                        layerRadius *
                        0.92f

                if (index == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }

            wavePath.close()

            linePaint.color = profile.color
            linePaint.alpha =
                (profile.lineAlpha -
                    layer * 25)
                    .coerceAtLeast(16)
            linePaint.strokeWidth =
                (2.4f -
                    layer * 0.32f)
                    .coerceAtLeast(0.85f)

            canvas.drawPath(
                wavePath,
                linePaint
            )
        }
    }

    private fun drawMainParticleBand(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        profile: VisualProfile
    ) {
        particlePaint.color = profile.color

        for (index in 0 until mainParticleCount) {
            val normalized =
                index.toFloat() /
                    mainParticleCount.toFloat()

            val angle =
                normalized *
                    PI.toFloat() *
                    2f

            val noise = stableNoise(index)
            val secondaryNoise =
                stableNoise(index + 811)

            val wave =
                sin(
                    angle * 4f +
                        phase * profile.speed
                ) *
                    profile.amplitude +
                    sin(
                        angle * 9f -
                            phase * 0.61f
                    ) *
                    profile.amplitude *
                    0.34f

            val bandOffset =
                (noise - 0.5f) *
                    radius *
                    0.31f

            val orbitalMotion =
                sin(
                    phase * 0.8f +
                        secondaryNoise *
                        PI.toFloat() *
                        2f
                ) *
                    radius *
                    0.022f

            val particleRadius =
                radius +
                    bandOffset +
                    wave +
                    orbitalMotion

            val x =
                centerX +
                    cos(angle) *
                    particleRadius

            val y =
                centerY +
                    sin(angle) *
                    particleRadius *
                    0.92f

            particlePaint.alpha =
                (profile.particleAlpha *
                    (0.35f +
                        noise * 0.65f))
                    .toInt()
                    .coerceIn(8, 255)

            val size =
                0.55f +
                    noise * 1.9f +
                    trafficLevel * 0.75f

            canvas.drawCircle(
                x,
                y,
                size,
                particlePaint
            )
        }
    }

    private fun drawFloatingDust(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        profile: VisualProfile
    ) {
        particlePaint.color = profile.color

        for (index in 0 until dustParticleCount) {
            val angleNoise = stableNoise(index + 2_000)
            val distanceNoise = stableNoise(index + 4_000)
            val alphaNoise = stableNoise(index + 6_000)

            val angle =
                angleNoise *
                    PI.toFloat() *
                    2f +
                    phase *
                    (0.08f +
                        alphaNoise * 0.09f)

            val distance =
                radius *
                    (1.04f +
                        distanceNoise * 0.42f)

            val x =
                centerX +
                    cos(angle) *
                    distance

            val y =
                centerY +
                    sin(angle) *
                    distance *
                    0.92f

            particlePaint.alpha =
                (profile.particleAlpha *
                    0.34f *
                    alphaNoise)
                    .toInt()
                    .coerceIn(5, 88)

            canvas.drawCircle(
                x,
                y,
                0.6f +
                    alphaNoise * 1.1f,
                particlePaint
            )
        }
    }

    private fun drawInnerHalo(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        profile: VisualProfile
    ) {
        ringPaint.color = profile.color
        ringPaint.alpha =
            profile.innerRingAlpha / 2
        ringPaint.strokeWidth = 15f

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.61f,
            ringPaint
        )

        ringPaint.alpha =
            profile.innerRingAlpha
        ringPaint.strokeWidth = 2.4f

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.56f,
            ringPaint
        )
    }

    private fun stableNoise(
        value: Int
    ): Float {
        val seed =
            sin(value * 12.9898f) *
                43_758.547f

        return seed - floor(seed)
    }

    private fun visualProfile():
        VisualProfile {
        val orange =
            Color.rgb(255, 91, 48)

        val dimOrange =
            Color.rgb(170, 78, 53)

        return when (state) {
            State.DISCONNECTED ->
                VisualProfile(
                    color = dimOrange,
                    amplitude = 7f,
                    speed = 0.48f,
                    particleAlpha = 105,
                    lineAlpha = 90,
                    glowAlpha = 24,
                    innerRingAlpha = 45
                )

            State.SCANNING ->
                VisualProfile(
                    color = orange,
                    amplitude = 14f,
                    speed = 1.25f,
                    particleAlpha = 190,
                    lineAlpha = 178,
                    glowAlpha = 60,
                    innerRingAlpha = 105
                )

            State.CONNECTING ->
                VisualProfile(
                    color = orange,
                    amplitude = 19f,
                    speed = 1.62f,
                    particleAlpha = 220,
                    lineAlpha = 205,
                    glowAlpha = 80,
                    innerRingAlpha = 150
                )

            State.CONNECTED ->
                VisualProfile(
                    color = orange,
                    amplitude =
                        17f +
                            trafficLevel * 8f,
                    speed =
                        1.08f +
                            trafficLevel * 0.72f,
                    particleAlpha = 238,
                    lineAlpha = 225,
                    glowAlpha =
                        88 +
                            (trafficLevel * 36f)
                                .toInt(),
                    innerRingAlpha = 185
                )

            State.DISCONNECTING ->
                VisualProfile(
                    color = dimOrange,
                    amplitude = 12f,
                    speed = 0.76f,
                    particleAlpha = 145,
                    lineAlpha = 130,
                    glowAlpha = 42,
                    innerRingAlpha = 82
                )

            State.ERROR ->
                VisualProfile(
                    color =
                        Color.rgb(255, 73, 79),
                    amplitude = 14f,
                    speed = 1.85f,
                    particleAlpha = 210,
                    lineAlpha = 195,
                    glowAlpha = 75,
                    innerRingAlpha = 145
                )
        }
    }

    private data class VisualProfile(
        val color: Int,
        val amplitude: Float,
        val speed: Float,
        val particleAlpha: Int,
        val lineAlpha: Int,
        val glowAlpha: Int,
        val innerRingAlpha: Int
    )
}
