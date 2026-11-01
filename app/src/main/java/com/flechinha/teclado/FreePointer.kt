package com.flechinha.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * Seta livre: a tela inteira vira touchpad (janela transparente de acessibilidade).
 *  - arrastar 1 dedo: anda com a seta
 *  - dois toques: clica onde a seta esta
 *  - segurar e arrastar: marca o texto e copia ao soltar
 *  - arrastar com 2 dedos: rola a tela onde a seta esta
 */
class FreePointer(private val svc: PointerAccess) {
    private var wm: WindowManager? = null
    private var v: FView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var ox = 0f
    private var oy = 0f
    private val loc = IntArray(2)

    val showing: Boolean get() = v != null

    fun refresh() { v?.invalidate() }

    fun show(): Boolean {
        if (v != null) return true
        return try {
            val m = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = FView(svc)
            val real = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            m.defaultDisplay.getRealMetrics(real)
            // faixa livre no topo: o painel de atalhos continua puxavel e voce sempre consegue desligar a seta
            val top = (56 * svc.resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, real.heightPixels - top,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.y = top
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            m.addView(view, params)
            wm = m; v = view; lp = params
            if (ox == 0f && oy == 0f) { ox = real.widthPixels / 2f; oy = (real.heightPixels - top) / 2f }
            true
        } catch (e: Exception) { v = null; false }
    }

    fun hide() {
        val view = v ?: return
        try { wm?.removeView(view) } catch (e: Exception) { }
        v = null
    }

    /** deixa os toques passarem para o app por um instante (para o clique/rolagem atingir o app e nao a janela da seta) */
    private fun pass(on: Boolean) {
        val view = v ?: return
        val p = lp ?: return
        p.flags = if (on) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                  else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { wm?.updateViewLayout(view, p) } catch (e: Exception) { }
    }

    private fun sx(): Int { v?.getLocationOnScreen(loc); return (ox + loc[0]).toInt() }
    private fun sy(): Int { v?.getLocationOnScreen(loc); return (oy + loc[1]).toInt() }

    private inner class FView(c: Context) : View(c) {
        private val dn = c.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lo = IntArray(2)
        private val accent = Cfg.acc(c)
        private val gain = Cfg.speed(c).coerceAtLeast(1f)
        private val slop = 10 * dn
        private val arrow = Path().apply {
            moveTo(4f, 3f); lineTo(4f, 21f); lineTo(8.6f, 16.8f); lineTo(11.6f, 23.4f)
            lineTo(14.6f, 22f); lineTo(11.6f, 15.6f); lineTo(18f, 15.6f); close()
        }

        private var selecting = false
        private var ringStart = 0L
        private var ringOk = true
        private var downT = 0L
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var scrollMode = false
        private var scrollDone = false
        private var sdx = 0f
        private var sdy = 0f
        private var slx = 0f
        private var sly = 0f
        private var lastTapT = 0L
        private var lastExt = 0L
        private var closing = false
        private val hold = Runnable { if (!moved && !scrollMode) beginSel() }

        private fun ring(ok: Boolean) { ringOk = ok; ringStart = SystemClock.uptimeMillis(); invalidate() }

        private fun movePointer(dx: Float, dy: Float) {
            ox = (ox + dx).coerceIn(0f, (if (width > 0) width else 1) - 1f)
            oy = (oy + dy).coerceIn(0f, (if (height > 0) height else 1) - 1f)
            invalidate()
        }

        private fun beginSel() {
            selecting = svc.begin(sx(), sy())
            if (selecting) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) else ring(false)
            invalidate()
        }

        private fun extendSel() {
            val n = SystemClock.uptimeMillis()
            if (n - lastExt < 45) return
            lastExt = n
            svc.extend(sx(), sy())
            invalidate()
        }

        private fun endSel() {
            svc.extend(sx(), sy())
            val ok = svc.copySelection()
            svc.end()
            selecting = false
            if (ok) Toast.makeText(svc, "Copiado", Toast.LENGTH_SHORT).show() else ring(false)
            invalidate()
        }

        private fun doClick() {
            val x = sx(); val y = sy()
            // 1) aperta direto o botao que esta embaixo da seta
            if (svc.clickNode(x, y)) { ring(true); return }
            // 2) senao, faz um toque de verdade (a janela da seta sai da frente por um instante)
            pass(true)
            postDelayed({
                val ok = svc.click(x, y) { pass(false) }
                if (!ok) pass(false)
                ring(ok)
            }, 130)
            postDelayed({ pass(false) }, 900)
        }

        private fun doScroll() {
            if (Math.abs(sdx) < 30f && Math.abs(sdy) < 30f) return
            val dm = resources.displayMetrics
            val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
            val x1 = sx().toFloat().coerceIn(40f, w - 40f)
            val y1 = sy().toFloat().coerceIn(40f, h - 40f)
            val x2 = (x1 + sdx * 1.6f).coerceIn(20f, w - 20f)
            val y2 = (y1 + sdy * 1.6f).coerceIn(20f, h - 20f)
            pass(true)
            postDelayed({
                val ok = svc.swipe(x1, y1, x2, y2, 320L) { pass(false) }
                if (!ok) pass(false)
                ring(ok)
            }, 130)
            postDelayed({ pass(false) }, 1000)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (closing) return true
            if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && e.pointerCount >= 3) {
                // tres dedos: desliga a seta
                closing = true
                removeCallbacks(hold)
                Handler(Looper.getMainLooper()).post {
                    svc.toggleFree()
                    Toast.makeText(svc, "Seta desligada", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downT = SystemClock.uptimeMillis(); downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                    moved = false; scrollMode = false; scrollDone = false; selecting = false
                    svc.clearVirtual(); invalidate()
                    postDelayed(hold, 380)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    removeCallbacks(hold)
                    if (!scrollMode) { scrollMode = true; scrollDone = false; sdx = 0f; sdy = 0f }
                    if (e.pointerCount >= 2) { slx = (e.getX(0) + e.getX(1)) / 2; sly = (e.getY(0) + e.getY(1)) / 2 }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scrollMode) {
                        if (e.pointerCount >= 2) {
                            val ax = (e.getX(0) + e.getX(1)) / 2
                            val ay = (e.getY(0) + e.getY(1)) / 2
                            sdx += ax - slx; sdy += ay - sly; slx = ax; sly = ay
                        }
                    } else {
                        if (!moved && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > slop) { moved = true; removeCallbacks(hold) }
                        movePointer((e.x - lastX) * gain, (e.y - lastY) * gain)
                        lastX = e.x; lastY = e.y
                        if (selecting) extendSel()
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (scrollMode && !scrollDone) { scrollDone = true; doScroll() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(hold)
                    val up = e.actionMasked == MotionEvent.ACTION_UP
                    if (selecting) { endSel(); lastTapT = 0L }
                    else if (scrollMode) { if (!scrollDone) { scrollDone = true; doScroll() }; lastTapT = 0L }
                    else if (up && !moved && SystemClock.uptimeMillis() - downT < 300) doClick()
                    else lastTapT = 0L
                    scrollMode = false
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val hl = svc.highlight()
            if (hl.isNotEmpty()) {
                getLocationOnScreen(lo)
                p.style = Paint.Style.FILL; p.color = (accent and 0x00FFFFFF) or 0x66000000
                for (r in hl) c.drawRect(r.left - lo[0], r.top - lo[1], r.right - lo[0], r.bottom - lo[1], p)
            }
            val rt = SystemClock.uptimeMillis() - ringStart
            if (ringStart > 0L && rt < 260L) {
                val f = rt / 260f
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f * dn
                p.color = if (ringOk) accent else 0xFFFF5555.toInt()
                p.alpha = (255 * (1f - f)).toInt()
                c.drawCircle(ox, oy, (10f + 26f * f) * dn, p)
                postInvalidateOnAnimation()
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
}
