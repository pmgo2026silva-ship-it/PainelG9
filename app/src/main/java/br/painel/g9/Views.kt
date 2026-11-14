package br.painel.g9

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Medidor em arco (velocidade, giro, etc.). */
class GaugeView(ctx: Context) : View(ctx) {
    var max = 100f
    var value = 0f
        set(v) { field = v; invalidate() }
    var accent = Color.RED
    var unit = ""
    var title = ""
    var text: String? = null
    var warn = false

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(51, 255, 255, 255)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = HEAD
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val st = s * 0.06f
        track.strokeWidth = st
        arc.strokeWidth = st
        val r = s / 2f - st
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 150f, 240f, false, track)
        val f = (value / max).coerceIn(0f, 1f)
        arc.color = if (warn) Color.parseColor("#FF3B3B") else accent
        if (f > 0f) c.drawArc(rect, 150f, 240f * f, false, arc)

        tp.color = Color.WHITE
        tp.textSize = s * 0.30f
        c.drawText(text ?: value.toInt().toString(), cx, cy + s * 0.08f, tp)
        tp.color = DIM
        tp.textSize = s * 0.085f
        c.drawText(unit, cx, cy + s * 0.20f, tp)
        tp.textSize = s * 0.075f
        c.drawText(title, cx, cy + r * 0.95f, tp)
    }
}

/** Mostra a foto PNG do carro do usuário ou, se não houver, uma silhueta de sedã na cor do tema. */
class CarView(ctx: Context) : View(ctx) {
    var accent = Color.RED
        set(v) { field = v; invalidate() }
    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Path()
    private val glass = Path()
    private val dst = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.isFilterBitmap = true

        // brilho no chão
        p.shader = RadialGradient(
            w / 2f, h * 0.80f, w * 0.48f,
            Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        c.drawOval(RectF(w * 0.04f, h * 0.68f, w * 0.96f, h * 0.96f), p)
        p.shader = null

        val bm = bitmap
        if (bm != null) {
            val sc = minOf(w / bm.width, h / bm.height) * 0.96f
            val dw = bm.width * sc
            val dh = bm.height * sc
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(bm, null, dst, p)
            return
        }

        val k = minOf(w / 100f, h / 36f) * 0.94f
        c.save()
        c.translate((w - 100f * k) / 2f, (h - 36f * k) / 2f)
        c.scale(k, k)

        body.reset()
        body.moveTo(3f, 26f)
        body.lineTo(4f, 20f)
        body.quadTo(5f, 17f, 12f, 16f)
        body.lineTo(28f, 14f)
        body.quadTo(36f, 3f, 46f, 2.5f)
        body.lineTo(62f, 2.5f)
        body.quadTo(72f, 3f, 80f, 13f)
        body.lineTo(93f, 16f)
        body.quadTo(98f, 18f, 97f, 24f)
        body.lineTo(96f, 26f)
        body.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 2f, 0f, 26f,
            Color.argb(255, minOf(255, Color.red(accent) + 50), minOf(255, Color.green(accent) + 40), minOf(255, Color.blue(accent) + 40)),
            Color.argb(255, Color.red(accent) * 6 / 10, Color.green(accent) * 6 / 10, Color.blue(accent) * 6 / 10),
            Shader.TileMode.CLAMP
        )
        c.drawPath(body, p)
        p.shader = null

        glass.reset()
        glass.moveTo(31f, 13.5f)
        glass.lineTo(38f, 6f)
        glass.lineTo(60f, 6f)
        glass.lineTo(74f, 13.5f)
        glass.close()
        p.color = Color.argb(230, 12, 16, 24)
        c.drawPath(glass, p)

        // rodas
        for (x in floatArrayOf(24f, 76f)) {
            p.color = Color.BLACK
            c.drawCircle(x, 27f, 8f, p)
            p.color = Color.parseColor("#1C222B")
            c.drawCircle(x, 27f, 6.6f, p)
            p.color = Color.parseColor("#B8C0CC")
            c.drawCircle(x, 27f, 3.6f, p)
            p.color = Color.parseColor("#3A4350")
            c.drawCircle(x, 27f, 1.2f, p)
        }
        // faróis
        p.color = Color.WHITE
        c.drawRect(93.5f, 17.5f, 97f, 20.5f, p)
        p.color = Color.parseColor("#FF2A2A")
        c.drawRect(3f, 19.5f, 6f, 22f, p)
        c.restore()
    }
}

/** Ícones desenhados em código (sem arquivos de imagem). */
class IconView(ctx: Context, kind0: IconView.Kind) : View(ctx) {
    enum class Kind { PREV, PLAY, PAUSE, NEXT, VOLDN, VOLUP, GRID, HOME, GAUGE, TUNE }

    var kind: Kind = kind0
        set(v) { field = v; invalidate() }

    var tint = Color.WHITE
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    private fun poly(c: Canvas, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.30f
        val cx = width / 2f
        val cy = height / 2f
        p.color = tint
        p.style = Paint.Style.FILL
        p.strokeWidth = s * 0.28f
        p.strokeCap = Paint.Cap.ROUND

        when (kind) {
            Kind.PLAY -> poly(c, cx - s * 0.5f, cy - s * 0.8f, cx - s * 0.5f, cy + s * 0.8f, cx + s * 0.8f, cy)
            Kind.PAUSE -> {
                c.drawRect(cx - s * 0.65f, cy - s * 0.75f, cx - s * 0.2f, cy + s * 0.75f, p)
                c.drawRect(cx + s * 0.2f, cy - s * 0.75f, cx + s * 0.65f, cy + s * 0.75f, p)
            }
            Kind.PREV -> {
                c.drawRect(cx - s * 0.8f, cy - s * 0.75f, cx - s * 0.5f, cy + s * 0.75f, p)
                poly(c, cx + s * 0.8f, cy - s * 0.75f, cx + s * 0.8f, cy + s * 0.75f, cx - s * 0.4f, cy)
            }
            Kind.NEXT -> {
                c.drawRect(cx + s * 0.5f, cy - s * 0.75f, cx + s * 0.8f, cy + s * 0.75f, p)
                poly(c, cx - s * 0.8f, cy - s * 0.75f, cx - s * 0.8f, cy + s * 0.75f, cx + s * 0.4f, cy)
            }
            Kind.VOLDN, Kind.VOLUP -> {
                poly(
                    c,
                    cx - s * 0.9f, cy - s * 0.3f, cx - s * 0.5f, cy - s * 0.3f, cx - s * 0.05f, cy - s * 0.75f,
                    cx - s * 0.05f, cy + s * 0.75f, cx - s * 0.5f, cy + s * 0.3f, cx - s * 0.9f, cy + s * 0.3f
                )
                c.drawLine(cx + s * 0.3f, cy, cx + s * 0.9f, cy, p)
                if (kind == Kind.VOLUP) c.drawLine(cx + s * 0.6f, cy - s * 0.3f, cx + s * 0.6f, cy + s * 0.3f, p)
            }
            Kind.GRID -> {
                val a = s * 0.12f
                val b = s * 0.78f
                c.drawRect(cx - b, cy - b, cx - a, cy - a, p)
                c.drawRect(cx + a, cy - b, cx + b, cy - a, p)
                c.drawRect(cx - b, cy + a, cx - a, cy + b, p)
                c.drawRect(cx + a, cy + a, cx + b, cy + b, p)
            }
            Kind.HOME -> {
                poly(c, cx - s * 0.95f, cy - s * 0.05f, cx, cy - s * 0.85f, cx + s * 0.95f, cy - s * 0.05f)
                c.drawRect(cx - s * 0.62f, cy - s * 0.05f, cx + s * 0.62f, cy + s * 0.8f, p)
            }
            Kind.GAUGE -> {
                p.style = Paint.Style.STROKE
                rf.set(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f)
                c.drawArc(rf, 150f, 240f, false, p)
                c.drawLine(cx, cy, cx + s * 0.5f, cy - s * 0.5f, p)
            }
            Kind.TUNE -> {
                c.drawLine(cx - s * 0.9f, cy - s * 0.6f, cx + s * 0.9f, cy - s * 0.6f, p)
                c.drawLine(cx - s * 0.9f, cy, cx + s * 0.9f, cy, p)
                c.drawLine(cx - s * 0.9f, cy + s * 0.6f, cx + s * 0.9f, cy + s * 0.6f, p)
                c.drawCircle(cx - s * 0.35f, cy - s * 0.6f, s * 0.28f, p)
                c.drawCircle(cx + s * 0.4f, cy, s * 0.28f, p)
                c.drawCircle(cx - s * 0.1f, cy + s * 0.6f, s * 0.28f, p)
            }
        }
    }
}

/** Fundo: degradê do tema, foto opcional e brilho colorido. */
class BackgroundView(ctx: Context) : View(ctx) {
    private var cTop = Color.BLACK
    private var cBottom = Color.BLACK
    private var cAccent = Color.RED
    private var photo: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()

    fun apply(t: PTheme, bmp: Bitmap?) {
        cTop = t.bgTop
        cBottom = t.bgBottom
        cAccent = t.accent
        photo = bmp
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h, cTop, cBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val bm = photo
        if (bm != null) {
            val sc = maxOf(w / bm.width, h / bm.height)
            m.reset()
            m.setScale(sc, sc)
            m.postTranslate((w - bm.width * sc) / 2f, (h - bm.height * sc) / 2f)
            p.isFilterBitmap = true
            c.drawBitmap(bm, m, p)
            p.color = Color.argb(170, 0, 0, 0)
            c.drawRect(0f, 0f, w, h, p)
        }

        p.shader = RadialGradient(
            w * 0.15f, 0f, w * 0.75f,
            Color.argb(80, Color.red(cAccent), Color.green(cAccent), Color.blue(cAccent)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }
}
