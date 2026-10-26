package com.jarvis.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import kotlin.math.min

/**
 * Stark-style HUD: glowing concentric arcs, a rotating sweep gradient, and a
 * soft reactive pulse. Draws only, no bitmaps, so it stays cheap on old phones.
 */
class HudView(context: Context) : View(context) {

    var listening = false
        set(value) {
            field = value
            invalidate()
        }

    private val cyan = Color.parseColor("#4FC3F7")
    private val deepCyan = Color.parseColor("#0277BD")
    private val gold = Color.parseColor("#FFD180")

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = cyan
    }

    private var sweep: SweepGradient? = null
    private val rect = RectF()
    private var rotation = 0f

    init {
        // Slow continuous spin of the outer ring, runs while the view is visible.
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 12000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        startAnimation(rotate)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        sweep = SweepGradient(cx, cy, intArrayOf(deepCyan, cyan, deepCyan), floatArrayOf(0f, 0.5f, 1f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f * 0.92f
        val listeningBoost = if (listening) 1.12f else 1f

        // Outer spinning ring with gradient sweep.
        arcPaint.shader = sweep
        arcPaint.color = cyan
        rect.set(cx - maxR, cy - maxR, cx + maxR, cy + maxR)
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawArc(rect, 300f, 220f, false, arcPaint)
        canvas.restore()

        // Inner solid arcs.
        arcPaint.shader = null
        arcPaint.color = cyan
        val r2 = maxR * 0.72f * listeningBoost
        rect.set(cx - r2, cy - r2, cx + r2, cy + r2)
        canvas.drawArc(rect, 40f, 110f, false, arcPaint)
        canvas.drawArc(rect, 220f, 110f, false, arcPaint)

        // Thin cross arcs.
        thinPaint.color = deepCyan
        val r3 = maxR * 0.5f
        rect.set(cx - r3, cy - r3, cx + r3, cy + r3)
        canvas.drawArc(rect, 0f, 360f, false, thinPaint)

        val r4 = maxR * 0.3f
        rect.set(cx - r4, cy - r4, cx + r4, cy + r4)
        thinPaint.color = gold
        canvas.drawArc(rect, 140f, 80f, false, thinPaint)

        // Tick marks around the outer edge.
        val rTick1 = maxR * 0.86f
        val rTick2 = maxR * 0.9f
        for (i in 0 until 36) {
            val a = Math.toRadians((i * 10).toDouble())
            val x1 = cx + (rTick1 * Math.cos(a)).toFloat()
            val y1 = cy + (rTick1 * Math.sin(a)).toFloat()
            val x2 = cx + (rTick2 * Math.cos(a)).toFloat()
            val y2 = cy + (rTick2 * Math.sin(a)).toFloat()
            tickPaint.alpha = if (i % 3 == 0) 220 else 90
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }
    }
}
