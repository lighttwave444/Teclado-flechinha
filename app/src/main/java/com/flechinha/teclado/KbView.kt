package com.flechinha.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class KbView(ctx: Context, private val svc: KeyboardService) : View(ctx) {
    private class K(val label: String, val type: Int, val v: String = label, val w: Float = 1f) { val r = RectF() }

    private companion object {
        const val CH = 0; const val SHIFT = 1; const val DEL = 2; const val SYM = 3; const val SPACE = 4
        const val ENTER = 5; const val ACT = 6; const val ALT = 7; const val BACK = 8; const val SEL = 9
        val ALTS = mapOf("a" to "áàâãä", "e" to "éèêë", "i" to "íìîï", "o" to "óòôõö", "u" to "úùûü", "c" to "ç", "n" to "ñ")
    }

    private val d = resources.displayMetrics.density
    private val g = 4 * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val h = Handler(Looper.getMainLooper())
    private var pal = Cfg.pal(ctx)
    private var acc = Cfg.acc(ctx)
    private var speed = Cfg.speed(ctx)
    private var vib = Cfg.vib(ctx)
    private var hs = Cfg.hs(ctx)
    private var sym = false
    private var shift = 0
    private var lastShift = 0L
    private var pad = false
    private var sel = false
    private var keys: List<K> = emptyList()
    private var alts: List<K> = emptyList()
    private val padRect = RectF()
    private var stripBottom = 0f
    private var down: K? = null
    private var inPad = false
    private var downX = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var accX = 0f
    private var accY = 0f
    private var px = 0f
    private var py = 0f
    private var spaceSwipe = false
    private var delRun = false
    private var delCount = 0
    private val rh: Float get() = 46 * d * hs

    fun reload() {
        pal = Cfg.pal(context); acc = Cfg.acc(context); speed = Cfg.speed(context); vib = Cfg.vib(context); hs = Cfg.hs(context)
        sym = false; pad = false; sel = false; alts = emptyList()
        val before = svc.currentInputConnection?.getTextBeforeCursor(1, 0)
        shift = if (before != null && before.isEmpty()) 1 else 0
        requestLayout(); layoutKeys(); invalidate()
    }

    override fun onMeasure(w: Int, hh: Int) {
        val total = g + 0.8f * rh + g + 4 * (rh + g)
        setMeasuredDimension(MeasureSpec.getSize(w), total.toInt())
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) { layoutKeys() }

    private fun chars(s: String) = s.map { K(it.toString(), CH) }

    private fun rows(): List<List<K>> {
        if (pad) return listOf(listOf(
            K("ABC", BACK, w = 1.2f), K("Selecionar", SEL, w = 1.8f), K("Tudo", ACT, "all"),
            K("Copiar", ACT, "copy"), K("Recortar", ACT, "cut", 1.4f), K("Colar", ACT, "paste")))
        val strip = listOf(K("Ponteiro", ACT, "pad"), K("Tudo", ACT, "all"), K("Copiar", ACT, "copy"), K("Colar", ACT, "paste"))
        val r1 = chars(if (sym) "1234567890" else "qwertyuiop")
        val r2 = chars(if (sym) "@#\$%&-+()/" else "asdfghjklç")
        val r3 = if (sym) chars("*\"':;!?~") + K("⌫", DEL, w = 1.5f)
        else listOf(K("⇧", SHIFT, w = 1.5f)) + chars("zxcvbnm") + K("⌫", DEL, w = 1.5f)
        val r4 = listOf(K(if (sym) "ABC" else "123", SYM, w = 1.5f), K(",", CH), K("espaço", SPACE, w = 5f), K(".", CH), K("↵", ENTER, w = 1.5f))
        return listOf(strip, r1, r2, r3, r4)
    }

    private fun place(row: List<K>, y: Float, hh: Float, w: Float) {
        var sum = 0f
        for (k in row) sum += k.w
        val unit = (w - g * (row.size + 1)) / sum
        var x = g
        for (k in row) { k.r.set(x, y, x + unit * k.w, y + hh); x += unit * k.w + g }
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        if (w == 0f) return
        val rs = rows()
        val out = ArrayList<K>()
        if (pad) {
            val y = height - rh - g
            place(rs[0], y, rh, w); out.addAll(rs[0])
            padRect.set(g, g, w - g, y - g); stripBottom = 0f
        } else {
            var y = g
            val sh = 0.8f * rh
            place(rs[0], y, sh, w); out.addAll(rs[0]); stripBottom = y + sh; y += sh + g
            for (i in 1 until rs.size) { place(rs[i], y, rh, w); out.addAll(rs[i]); y += rh + g }
        }
        keys = out
    }

    private fun hit(list: List<K>, x: Float, y: Float): K? {
        val m = g / 2
        return list.firstOrNull { x >= it.r.left - m && x <= it.r.right + m && y >= it.r.top - m && y <= it.r.bottom + m }
    }

    private fun buzz() { if (vib) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    private fun commit(t: String) { svc.currentInputConnection?.commitText(t, 1) }

    private fun afterChar() { if (shift == 1) { shift = 0 } }

    private fun dpad(code: Int) {
        val ic = svc.currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        val meta = if (sel) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun menu(id: Int) { svc.currentInputConnection?.performContextMenuAction(id) }

    private fun enter() {
        val ei = svc.currentInputEditorInfo
        val opt = ei?.imeOptions ?: 0
        val a = opt and EditorInfo.IME_MASK_ACTION
        val noAct = (opt and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (ei != null && !noAct && a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED)
            svc.currentInputConnection?.performEditorAction(a)
        else svc.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    private fun press(k: K) {
        when (k.type) {
            CH -> { commit(if (!sym && shift > 0) k.v.uppercase() else k.v); afterChar() }
            SHIFT -> {
                val n = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (shift == 1 && n - lastShift < 350) 2 else if (shift == 1) 0 else 1
                lastShift = n
            }
            SYM -> sym = !sym
            SPACE -> commit(" ")
            ENTER -> enter()
            ACT -> when (k.v) {
                "pad" -> { pad = true; sel = false }
                "all" -> menu(android.R.id.selectAll)
                "copy" -> menu(android.R.id.copy)
                "cut" -> menu(android.R.id.cut)
                "paste" -> menu(android.R.id.paste)
            }
            BACK -> { pad = false; sel = false }
            SEL -> sel = !sel
        }
        layoutKeys(); invalidate()
    }

    private val delTask = object : Runnable {
        override fun run() {
            if (!delRun) return
            svc.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            delCount++
            h.postDelayed(this, if (delCount < 2) 380L else 55L)
        }
    }

    private val longTask = Runnable {
        val k = down ?: return@Runnable
        val s = ALTS[k.label] ?: return@Runnable
        val wk = 40 * d
        var x = (k.r.centerX() - s.length * wk / 2).coerceIn(g, width - s.length * wk - g)
        val list = ArrayList<K>()
        for (ch in s) {
            val t = if (shift > 0) ch.toString().uppercase() else ch.toString()
            val a = K(t, ALT); a.r.set(x, g, x + wk, stripBottom); x += wk; list.add(a)
        }
        alts = list; down = null; buzz(); invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; lastX = x; lastY = y; accX = 0f; accY = 0f; spaceSwipe = false
                if (pad && padRect.contains(x, y)) { inPad = true; px = x; py = y; buzz(); invalidate(); return true }
                val k = hit(keys, x, y); down = k
                if (k != null) {
                    buzz()
                    if (k.type == DEL) { delRun = true; delCount = 0; h.post(delTask) }
                    else if (k.type == CH && !sym && ALTS.containsKey(k.label)) h.postDelayed(longTask, 380)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (inPad) {
                    px = x; py = y
                    accX += x - lastX; accY += y - lastY
                    val st = 20 * d / speed
                    while (accX >= st) { dpad(KeyEvent.KEYCODE_DPAD_RIGHT); accX -= st }
                    while (accX <= -st) { dpad(KeyEvent.KEYCODE_DPAD_LEFT); accX += st }
                    while (accY >= st) { dpad(KeyEvent.KEYCODE_DPAD_DOWN); accY -= st }
                    while (accY <= -st) { dpad(KeyEvent.KEYCODE_DPAD_UP); accY += st }
                    invalidate()
                } else if (alts.isNotEmpty()) {
                    down = hit(alts, x, y); invalidate()
                } else if (down?.type == SPACE) {
                    if (spaceSwipe || Math.abs(x - downX) > 14 * d) {
                        spaceSwipe = true; accX += x - lastX
                        val st = 14 * d / speed
                        while (accX >= st) { dpad(KeyEvent.KEYCODE_DPAD_RIGHT); accX -= st }
                        while (accX <= -st) { dpad(KeyEvent.KEYCODE_DPAD_LEFT); accX += st }
                    }
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                h.removeCallbacks(longTask); h.removeCallbacks(delTask); delRun = false
                val up = e.actionMasked == MotionEvent.ACTION_UP
                if (inPad) { inPad = false; invalidate(); return true }
                if (alts.isNotEmpty()) {
                    val s = down
                    if (up && s != null && s.type == ALT) { commit(s.v); afterChar() }
                    alts = emptyList(); down = null; invalidate(); return true
                }
                val k = down; down = null
                if (k != null && up && k.type != DEL && !(k.type == SPACE && spaceSwipe)) press(k)
                spaceSwipe = false; invalidate()
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(pal.bg)
        if (pad) {
            paint.style = Paint.Style.FILL; paint.color = pal.keyAlt
            c.drawRoundRect(padRect, 12 * d, 12 * d, paint)
            paint.color = (pal.txt and 0x00FFFFFF) or 0x66000000
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 14 * d
            c.drawText(if (sel) "Selecionando: arraste para marcar o texto" else "Arraste aqui para mover o cursor", padRect.centerX(), padRect.centerY(), paint)
            if (inPad) {
                val p = Path()
                p.moveTo(px, py); p.lineTo(px, py + 22 * d); p.lineTo(px + 6 * d, py + 16 * d); p.lineTo(px + 11 * d, py + 26 * d)
                p.lineTo(px + 15 * d, py + 24 * d); p.lineTo(px + 10 * d, py + 14 * d); p.lineTo(px + 18 * d, py + 14 * d); p.close()
                paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt(); c.drawPath(p, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * d; paint.color = acc; c.drawPath(p, paint)
                paint.style = Paint.Style.FILL
            }
        }
        for (k in keys) { if (alts.isNotEmpty() && k.r.bottom <= stripBottom) continue; drawKey(c, k) }
        for (k in alts) drawKey(c, k)
    }

    private fun drawKey(c: Canvas, k: K) {
        val on = k === down || (k.type == SHIFT && shift > 0) || (k.type == SEL && sel) || k.type == ENTER
        paint.style = Paint.Style.FILL
        paint.color = if (on) acc else if (k.type == CH || k.type == SPACE || k.type == ALT) pal.key else pal.keyAlt
        c.drawRoundRect(k.r, 8 * d, 8 * d, paint)
        paint.color = if (on) 0xFFFFFFFF.toInt() else pal.txt
        paint.textAlign = Paint.Align.CENTER
        val t = if (k.type == CH && !sym && shift > 0) k.label.uppercase() else k.label
        paint.textSize = (if (k.label.length > 1 && k.type != ALT) 13 else 19) * d
        c.drawText(t, k.r.centerX(), k.r.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }
}
