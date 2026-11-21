package fr.ahmad.yuto.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Rideau opaque posé sur la vidéo quand la voiture roule (ou qu'on ne sait pas encore
 * si elle est arrêtée). Le son de la vidéo continue derrière.
 */
class LockView(context: Context) : View(context) {

    var density = 1f

    var title = ""
        set(v) { field = v; invalidate() }
    var subtitle = ""
        set(v) { field = v; invalidate() }
    /** 0..1 : progression de l'attente avant d'afficher la vidéo, -1 = pas d'attente en cours. */
    var progress = -1f
        set(v) { field = v; invalidate() }

    private val bg = Paint().apply { color = Color.rgb(9, 12, 17) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(34, 41, 52)
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ACCENT
        strokeCap = Paint.Cap.ROUND
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 162, 178) }
    private val oval = RectF()

    // Les touches ne traversent pas le rideau
    override fun onTouchEvent(event: MotionEvent?) = true

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)

        val d = density
        val r = min(w, h) * 0.13f
        val cx = w / 2f
        val cy = h * 0.36f
        track.strokeWidth = 7f * d
        arc.strokeWidth = 7f * d
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawOval(oval, track)
        if (progress >= 0f) canvas.drawArc(oval, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)

        // Deux barres "pause" au centre
        val bw = r * 0.22f
        val bh = r * 0.8f
        val gap = r * 0.18f
        canvas.drawRoundRect(cx - gap - bw, cy - bh / 2, cx - gap, cy + bh / 2, bw / 3, bw / 3, glyph)
        canvas.drawRoundRect(cx + gap, cy - bh / 2, cx + gap + bw, cy + bh / 2, bw / 3, bw / 3, glyph)

        val textW = (w * 0.8f).toInt().coerceAtLeast(1)
        titlePaint.textSize = 26f * d
        subPaint.textSize = 17f * d
        var y = cy + r + 26f * d
        y = drawCentered(canvas, title, titlePaint, textW, cx, y) + 10f * d
        drawCentered(canvas, subtitle, subPaint, textW, cx, y)
    }

    private fun drawCentered(c: Canvas, text: String, p: TextPaint, width: Int, cx: Float, top: Float): Float {
        if (text.isEmpty()) return top
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()
        c.save()
        c.translate(cx - width / 2f, top)
        layout.draw(c)
        c.restore()
        return top + layout.height
    }

    companion object {
        val ACCENT: Int = Color.rgb(198, 240, 60)
    }
}
