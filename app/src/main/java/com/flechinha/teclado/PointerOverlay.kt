package com.flechinha.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** A seta que anda por cima de toda a tela (janela transparente que nao pega toque). */
class PointerOverlay(private val ctx: Context) {
    private var wm: WindowManager? = null
    private var v: PView? = null
    private var ox = 0f
    private var oy = 0f
    private val loc = IntArray(2)
    private var hl: List<RectF> = emptyList()

    val showing: Boolean get() = v != null

    companion object {
        fun can(c: Context): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)
    }

    private inner class PView(c: Context) : View(c) {
        var selecting = false
        var accent = 0xFF2F7BFF.toInt()
        private val dn = c.resources.displayMetrics.density
        private val lo = IntArray(2)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arrow = Path().apply {
            moveTo(4f, 3f); lineTo(4f, 21f); lineTo(8.6f, 16.8f); lineTo(11.6f, 23.4f)
            lineTo(14.6f, 22f); lineTo(11.6f, 15.6f); lineTo(18f, 15.6f); close()
        }

        override fun onDraw(c: Canvas) {
            if (hl.isNotEmpty()) {
                getLocationOnScreen(lo)
                p.style = Paint.Style.FILL; p.color = (accent and 0x00FFFFFF) or 0x66000000
                for (r in hl) c.drawRect(r.left - lo[0], r.top - lo[1], r.right - lo[0], r.bottom - lo[1], p)
            }
            val s = 30f * dn / 24f
            c.save(); c.translate(ox - 4 * s, oy - 3 * s); c.scale(s, s)
            p.style = Paint.Style.FILL; p.color = if (selecting) accent else 0xFFFFFFFF.toInt()
            c.drawPath(arrow, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.strokeJoin = Paint.Join.ROUND
            p.color = 0xFF111111.toInt()
            c.drawPath(arrow, p)
            c.restore()
        }
    }

    private fun w(): Float { val vv = v; return if (vv != null && vv.width > 0) vv.width.toFloat() else ctx.resources.displayMetrics.widthPixels.toFloat() }
    private fun h(): Float { val vv = v; return if (vv != null && vv.height > 0) vv.height.toFloat() else ctx.resources.displayMetrics.heightPixels.toFloat() }

    fun show(): Boolean {
        if (v != null) return true
        if (!can(ctx)) return false
        return try {
            val m = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = PView(ctx)
            view.accent = Cfg.acc(ctx)
            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                       else WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            m.addView(view, lp)
            wm = m; v = view
            if (ox == 0f && oy == 0f) { ox = w() / 2; oy = h() / 2 }
            true
        } catch (e: Exception) { v = null; false }
    }

    fun hide() {
        val view = v ?: return
        try { wm?.removeView(view) } catch (e: Exception) { }
        v = null; hl = emptyList()
    }

    fun moveBy(dx: Float, dy: Float) {
        ox = (ox + dx).coerceIn(0f, w() - 1)
        oy = (oy + dy).coerceIn(0f, h() - 1)
        v?.invalidate()
    }

    fun setHighlight(l: List<RectF>) { hl = l; v?.invalidate() }

    fun setSelecting(b: Boolean) { v?.let { it.selecting = b; it.invalidate() } }

    /** posicao da ponta da seta em coordenadas da tela */
    fun sx(): Int { v?.getLocationOnScreen(loc); return (ox + loc[0]).toInt() }
    fun sy(): Int { v?.getLocationOnScreen(loc); return (oy + loc[1]).toInt() }
}
