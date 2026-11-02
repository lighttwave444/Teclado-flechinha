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
 * v10: dois toques e segurar, depois arrastar = arrasta coisas (apps, icones). Seta empurrada contra a borda rola a tela.
 * Seta livre: a tela inteira (acima do teclado, se ele estiver aberto) vira touchpad (janela transparente de acessibilidade).
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
    private var topPx = 0
    private var fullH = 0
    private var fullW = 0
    private val h = Handler(Looper.getMainLooper())
    // checagem de reserva: o teclado pode abrir/fechar sem avisar por evento
    private val poll = object : Runnable {
        override fun run() { if (v == null) return; fit(); h.postDelayed(this, 500) }
    }

    /**
     * Encolhe a area da seta para ficar so acima do teclado (quando ele aparece) e
     * volta para a tela inteira quando o teclado fecha. Assim a seta nao atrapalha as teclas.
     */
    fun fit() {
        val view = v ?: return
        val p = lp ?: return
        val minH = (80 * svc.resources.displayMetrics.density).toInt()
        // tamanho real da tela agora (muda quando o celular vira na horizontal)
        val real = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(real)
        if (real.heightPixels > 0) { fullH = real.heightPixels; fullW = real.widthPixels }
        val ime = svc.imeTop()
        val bottom = if (ime > topPx + minH) ime else fullH
        val nh = (bottom - topPx).coerceAtLeast(minH)
        if (p.height != nh) {
            p.height = nh
            try { wm?.updateViewLayout(view, p) } catch (e: Exception) { return }
        }
        // mantem a seta dentro da area nova
        ox = ox.coerceIn(0f, (fullW - 1).coerceAtLeast(1).toFloat())
        oy = oy.coerceIn(0f, nh - 1f)
        view.invalidate()
    }

    private fun realW(): Int = if (fullW > 0) fullW else svc.resources.displayMetrics.widthPixels
    private fun realH(): Int = if (fullH > 0) fullH else svc.resources.displayMetrics.heightPixels

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
            topPx = top; fullH = real.heightPixels; fullW = real.widthPixels
            if (ox == 0f && oy == 0f) { ox = real.widthPixels / 2f; oy = (real.heightPixels - top) / 2f }
            fit()
            h.removeCallbacks(poll); h.postDelayed(poll, 500)
            true
        } catch (e: Exception) { v = null; false }
    }

    fun hide() {
        h.removeCallbacks(poll)
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
        // dois toques + segurar + arrastar = arrastar coisas (apps, icones, itens do sistema)
        private var dragArmed = false
        private var dragging = false
        private var dragT = 0L
        private val trail = ArrayList<FloatArray>()
        private var pendingClick = false
        private var lastUpT = 0L
        private val clickRun = Runnable { pendingClick = false; if (v != null) doClick() }
        // empurrar a seta contra a borda: ao soltar, rola para mostrar o que esta alem
        private var pushX = 0f
        private var pushY = 0f
        private var edgeX = 0
        private var edgeY = 0
        private val hold = Runnable {
            if (!moved && !scrollMode) {
                val x = sx(); val y = sy()
                if (dragArmed) beginDrag()
                else if (svc.onLabel(x, y)) beginSel()          // em cima das letras: marca e copia
                else if (svc.hasTarget(x, y)) beginDrag()       // em cima do icone: pega e move
                else ring(false)
            }
        }

        private fun ring(ok: Boolean) { ringOk = ok; ringStart = SystemClock.uptimeMillis(); invalidate() }

        private fun movePointer(dx: Float, dy: Float) {
            val mx = (if (width > 0) width else 1) - 1f
            val my = (if (height > 0) height else 1) - 1f
            val nx = ox + dx; val ny = oy + dy
            ox = nx.coerceIn(0f, mx)
            oy = ny.coerceIn(0f, my)
            // acumula o quanto o dedo empurrou alem da borda
            pushX = if (nx > mx || nx < 0f) pushX + (nx - ox) else 0f
            pushY = if (ny > my || ny < 0f) pushY + (ny - oy) else 0f
            val th = 18 * dn
            if (pushX > th) edgeX = 1 else if (pushX < -th) edgeX = -1
            if (pushY > th) edgeY = 1 else if (pushY < -th) edgeY = -1
            invalidate()
        }

        private fun beginDrag() {
            dragging = true
            trail.clear()
            trail.add(floatArrayOf(sx().toFloat(), sy().toFloat()))
            dragT = SystemClock.uptimeMillis()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }

        private fun addTrail() {
            val x = sx().toFloat(); val y = sy().toFloat()
            val l = trail.lastOrNull()
            if (l == null || Math.hypot((x - l[0]).toDouble(), (y - l[1]).toDouble()) >= 6 * dn) trail.add(floatArrayOf(x, y))
        }

        private fun endDrag() {
            dragging = false
            addTrail()
            val last = floatArrayOf(sx().toFloat(), sy().toFloat())
            val pts = ArrayList(trail); trail.clear()
            if (pts.isEmpty() || pts.last()[0] != last[0] || pts.last()[1] != last[1]) pts.add(last)
            invalidate()
            if (pts.size < 2) {
                // segurou o icone sem mover: segura de verdade (abre o menu do icone)
                val x = last[0].toInt(); val y = last[1].toInt()
                pass(true)
                postDelayed({
                    val ok = svc.longPress(x, y) { pass(false) }
                    if (!ok) pass(false)
                    ring(ok)
                }, 130)
                postDelayed({ pass(false) }, 1300)
                return
            }
            val moveMs = (SystemClock.uptimeMillis() - dragT).coerceIn(300L, 5000L)
            pass(true)
            postDelayed({
                val ok = svc.dragPath(pts, moveMs) { pass(false) }
                if (!ok) pass(false)
                ring(ok)
            }, 130)
            postDelayed({ pass(false) }, moveMs + 1500L)
        }

        /** rola na direcao em que a seta foi empurrada contra a borda (apps recentes, listas, paginas) */
        private fun edgeScroll() {
            val dx = edgeX; val dy = edgeY
            edgeX = 0; edgeY = 0; pushX = 0f; pushY = 0f
            if (dx == 0 && dy == 0) return
            val sw = realW().toFloat(); val sh = realH().toFloat()
            val px = sx().toFloat(); val py = sy().toFloat()
            val sideIn = 52 * dn; val botIn = 120 * dn; val topIn = 90 * dn
            val x1: Float; val y1: Float; val x2: Float; val y2: Float
            if (dx != 0) {
                // borda esquerda/direita: arrasta na horizontal
                val len = sw * 0.5f
                y1 = py.coerceIn(topIn, sh - botIn); y2 = y1
                if (dx > 0) { x1 = Math.min(px, sw - sideIn); x2 = (x1 - len).coerceAtLeast(20f) }
                else { x1 = Math.max(px, sideIn); x2 = (x1 + len).coerceAtMost(sw - 20f) }
            } else {
                // borda de cima/baixo: arrasta na vertical
                val len = sh * 0.4f
                x1 = px.coerceIn(sideIn, sw - sideIn); x2 = x1
                if (dy > 0) { y1 = Math.min(py, sh - botIn); y2 = (y1 - len).coerceAtLeast(20f) }
                else { y1 = Math.max(py, topIn); y2 = (y1 + len).coerceAtMost(sh - 20f) }
            }
            pass(true)
            postDelayed({
                val ok = svc.swipe(x1, y1, x2, y2, 320L) { pass(false) }
                if (!ok) pass(false)
                ring(ok)
            }, 130)
            postDelayed({ pass(false) }, 1000)
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
                removeCallbacks(hold); removeCallbacks(clickRun); pendingClick = false
                Handler(Looper.getMainLooper()).post {
                    svc.toggleFree()
                    Toast.makeText(svc, "Seta desligada", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downT = SystemClock.uptimeMillis(); downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                    moved = false; scrollMode = false; scrollDone = false; selecting = false; dragging = false
                    edgeX = 0; edgeY = 0; pushX = 0f; pushY = 0f
                    // segundo toque logo depois do primeiro: se segurar, vira arrastar
                    dragArmed = pendingClick && downT - lastUpT < 400
                    if (pendingClick) { removeCallbacks(clickRun); pendingClick = false }
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
                        if (dragging) addTrail()
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (scrollMode && !scrollDone) { scrollDone = true; doScroll() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(hold)
                    val up = e.actionMasked == MotionEvent.ACTION_UP
                    if (dragging) { if (up) endDrag() else { dragging = false; trail.clear(); invalidate() }; lastTapT = 0L }
                    else if (selecting) { endSel(); lastTapT = 0L }
                    else if (scrollMode) { if (!scrollDone) { scrollDone = true; doScroll() }; lastTapT = 0L }
                    else if (up && moved && (edgeX != 0 || edgeY != 0)) { edgeScroll(); lastTapT = 0L }
                    else if (up && !moved && SystemClock.uptimeMillis() - downT < 300) {
                        if (dragArmed) doClick()   // dois toques rapidos: clica
                        else { pendingClick = true; lastUpT = SystemClock.uptimeMillis(); postDelayed(clickRun, 280) }
                    }
                    else lastTapT = 0L
                    scrollMode = false; dragArmed = false
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
            if (dragging && trail.size > 1) {
                getLocationOnScreen(lo)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f * dn; p.strokeCap = Paint.Cap.ROUND
                p.color = (accent and 0x00FFFFFF) or 0xAA000000.toInt()
                val tp = Path()
                tp.moveTo(trail[0][0] - lo[0], trail[0][1] - lo[1])
                for (i in 1 until trail.size) tp.lineTo(trail[i][0] - lo[0], trail[i][1] - lo[1])
                c.drawPath(tp, p)
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
            p.style = Paint.Style.FILL; p.color = if (selecting || dragging) accent else 0xFFFFFFFF.toInt()
            c.drawPath(arrow, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.strokeJoin = Paint.Join.ROUND
            p.color = 0xFF111111.toInt()
            c.drawPath(arrow, p)
            c.restore()
        }
    }
}
