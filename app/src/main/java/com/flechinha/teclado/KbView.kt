package com.flechinha.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest

/**
 * Teclado igual ao do launcher: painel de vidro, teclas de vidro, faixa de cima com
 * ponteiro / setas / Colar / fechar, tres camadas (letras, numeros, simbolos),
 * maiuscula automatica, acentos ao segurar e modo ponteiro (touchpad para mover o cursor e selecionar).
 */
class KbView(ctx: Context, private val svc: KeyboardService) : View(ctx) {
    private class K(val label: String, val type: Int, val v: String = label, val w: Float = 1f) {
        val r = RectF()
        var sp = false        // tecla especial (fundo mais fraco)
        var pill = false      // formato de pilula (faixa de cima)
        var icon = 0
        var rep = false       // repete enquanto segura
        var letter = false    // vira maiuscula com o shift
        var alt: String? = null
        var dim = false       // apagada no modo ponteiro
    }

    private companion object {
        const val CH = 0; const val SHIFT = 1; const val DEL = 2; const val LAYER = 3; const val SPACE = 4
        const val ENTER = 5; const val ACT = 6; const val ALT = 7; const val PTR = 8; const val ARROW = 9; const val HIDE = 10
        const val IC_PTR = 1; const val IC_L = 2; const val IC_R = 3; const val IC_DN = 4
        const val IC_SH = 5; const val IC_BK = 6; const val IC_EN = 7
        const val ABC = 0; const val NUM = 1; const val SYM = 2
        val ALTS = mapOf("a" to "áàâãä", "e" to "éèêë", "i" to "íìîï", "o" to "óòôõö", "u" to "úùûü", "c" to "çć", "n" to "ñ")
    }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val h = Handler(Looper.getMainLooper())
    private val ov = PointerOverlay(svc)
    private var selecting = false   // selecao em andamento na tela (servico de acessibilidade)
    private var lastExt = 0L
    private val tfKey: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold: Typeface = Typeface.DEFAULT_BOLD
    private var pal = Cfg.pal(ctx)
    private var acc = Cfg.acc(ctx)
    private var speed = Cfg.speed(ctx)
    private var vib = Cfg.vib(ctx)
    private var hs = Cfg.hs(ctx)

    // estado
    private var layer = ABC
    private var shift = 0
    private var lastShift = 0L
    private var pad = false
    private var sel = false
    private var selMode = true   // true: arrastar seleciona; false: arrastar so move o cursor
    private var keys: List<K> = emptyList()
    private var alts: List<K> = emptyList()
    private val popupRect = RectF()
    private val padRect = RectF()
    private val tmp = RectF()
    private val mat = Matrix()
    private val panelPath = Path()
    private var glass: LinearGradient? = null
    private var panelShader: LinearGradient? = null

    // medidas (como o "cqw" do launcher: 1 cq = 1% da largura)
    private var cq = 0f
    private var keyH = 0f
    private var stripH = 0f
    private var smH = 0f
    private var smW = 0f
    private var sideP = 0f
    private var topP = 0f
    private var gapR = 0f
    private var gapK = 0f
    private var botP = 0f

    // toque
    private var down: K? = null
    private var inPad = false
    private var padMoved = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var accX = 0f
    private var accY = 0f
    private var px = 0f
    private var py = 0f
    private var spaceSwipe = false
    private var repRun = false
    private var repCount = 0
    private var repK: K? = null

    // icones (caixa de 24 x 24, os mesmos desenhos do launcher)
    private val icons: Map<Int, Path> = mapOf(
        IC_PTR to Path().apply {
            moveTo(4f, 3f); lineTo(4f, 21f); lineTo(8.6f, 16.8f); lineTo(11.6f, 23.4f)
            lineTo(14.6f, 22f); lineTo(11.6f, 15.6f); lineTo(18f, 15.6f); close()
        },
        IC_L to Path().apply { moveTo(14f, 6f); lineTo(8f, 12f); lineTo(14f, 18f) },
        IC_R to Path().apply { moveTo(10f, 6f); lineTo(16f, 12f); lineTo(10f, 18f) },
        IC_DN to Path().apply { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) },
        IC_SH to Path().apply {
            moveTo(12f, 4f); lineTo(20f, 13f); lineTo(15f, 13f); lineTo(15f, 19f)
            lineTo(9f, 19f); lineTo(9f, 13f); lineTo(4f, 13f); close()
        },
        IC_BK to Path().apply {
            moveTo(9f, 5f); lineTo(20f, 5f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(20f, 19f); lineTo(9f, 19f); lineTo(3f, 12f); close()
            moveTo(12f, 9f); lineTo(18f, 15f); moveTo(18f, 9f); lineTo(12f, 15f)
        },
        IC_EN to Path().apply {
            moveTo(20f, 6f); lineTo(20f, 12f); lineTo(18f, 14f); lineTo(6f, 14f)
            moveTo(10f, 10f); lineTo(6f, 14f); lineTo(10f, 18f)
        }
    )

    // ---------- ciclo de vida ----------

    fun reload() {
        pal = Cfg.pal(context); acc = Cfg.acc(context); speed = Cfg.speed(context); vib = Cfg.vib(context); hs = Cfg.hs(context)
        layer = ABC; pad = false; sel = false; alts = emptyList(); down = null; inPad = false
        selecting = false; ov.hide()
        shift = 0
        auto()
        if (width > 0) { dims(width.toFloat()); buildShaders() }
        requestLayout(); layoutKeys(); invalidate()
    }

    /** chamado quando o cursor muda de lugar: refaz a maiuscula automatica */
    fun caps() {
        val old = shift
        auto()
        if (old != shift) invalidate()
    }

    private fun dims(w: Float) {
        cq = w / 100f
        keyH = maxOf(42 * d, 11.2f * cq) * hs
        stripH = maxOf(34 * d, 8.4f * cq)
        smH = maxOf(32 * d, 7.8f * cq)
        smW = 12f * cq
        sideP = 1.2f * cq; topP = 1.4f * cq; gapR = 1.5f * cq; gapK = 1.4f * cq; botP = 2.6f * cq
    }

    private fun totalH() = topP + stripH + 4 * gapR + 4 * keyH + botP

    private fun buildShaders() {
        glass = LinearGradient(0f, 0f, 0f, keyH, pal.glassTop, pal.glassBot, Shader.TileMode.CLAMP)
        panelShader = LinearGradient(0f, 0f, 0f, maxOf(height.toFloat(), 1f), pal.panelTop, pal.panelBot, Shader.TileMode.CLAMP)
    }

    override fun onMeasure(w: Int, hh: Int) {
        val ww = MeasureSpec.getSize(w)
        dims(ww.toFloat())
        setMeasuredDimension(ww, totalH().toInt())
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) {
        dims(w.toFloat()); buildShaders(); layoutKeys()
    }

    // ---------- teclas ----------

    private fun k(label: String, type: Int, v: String = label, w: Float = 1f, init: K.() -> Unit = {}): K =
        K(label, type, v, w).apply(init)

    private fun chars(s: String, w: Float = 1f, letters: Boolean = false): List<K> =
        s.map { ch ->
            val t = ch.toString()
            k(t, CH, w = w) { letter = letters; alt = if (letters) ALTS[t] else null }
        }

    private fun strip(): List<K> = listOf(
        k("", PTR) { sp = true; pill = true; icon = IC_PTR },
        k("", ARROW, "l") { sp = true; pill = true; icon = IC_L; rep = true; dim = pad },
        k("", ARROW, "r") { sp = true; pill = true; icon = IC_R; rep = true; dim = pad },
        k("Colar", ACT, "paste") { sp = true; pill = true },
        k("", HIDE) { sp = true; pill = true; icon = IC_DN; dim = pad }
    )

    private fun rows(): List<List<K>> {
        val bottom = listOf(
            k(if (layer == ABC) "?123" else "ABC", LAYER, if (layer == ABC) "num" else "abc", 2f) { sp = true },
            k(",", CH, w = 1.1f),
            k("espaço", SPACE, w = 5f),
            k(".", CH, w = 1.1f),
            k("", ENTER, w = 2f) { sp = true; icon = IC_EN }
        )
        val del = k("", DEL, w = 1.5f) { sp = true; icon = IC_BK; rep = true }
        return when (layer) {
            ABC -> listOf(
                chars("qwertyuiop", letters = true),
                chars("asdfghjklç", letters = true),
                listOf(k("", SHIFT, w = 1.5f) { sp = true; icon = IC_SH }) + chars("zxcvbnm", letters = true) + del,
                bottom
            )
            NUM -> listOf(
                chars("1234567890"),
                listOf("-", "/", ":", ";", "(", ")", "R$", "&", "@", "\"").map { t ->
                    k(t, CH) { alt = if (t == "R$") "$€£¥" else null }
                },
                listOf(k("#+=", LAYER, "sym", 1.5f) { sp = true }) + chars(".,?!'", 1.4f) + del,
                bottom
            )
            else -> listOf(
                chars("[]{}#%^*+="),
                chars("_\\|~<>€£¥•"),
                listOf(k("123", LAYER, "num", 1.5f) { sp = true }) + chars(".,?!'", 1.4f) + del,
                bottom
            )
        }
    }

    private fun padRow(): List<K> = listOf(
        k("Marcar", ACT, "mode") { sp = true },
        k("Tudo", ACT, "all") { sp = true },
        k("Copiar", ACT, "copy") { sp = true },
        k("Recortar", ACT, "cut") { sp = true }
    )

    private fun place(row: List<K>, y: Float, hh: Float) {
        var sum = 0f
        for (kk in row) sum += kk.w
        val unit = (width - 2 * sideP - gapK * (row.size - 1)) / sum
        var x = sideP
        for (kk in row) { kk.r.set(x, y, x + unit * kk.w, y + hh); x += unit * kk.w + gapK }
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        if (w == 0f) return
        if (cq == 0f) dims(w)
        val out = ArrayList<K>()
        // faixa de cima: ponteiro, seta esq., seta dir., [Colar], fechar
        val st = strip()
        val cy = topP + stripH / 2
        val top = cy - smH / 2
        val bot = cy + smH / 2
        var x = sideP
        st[0].r.set(x, top, x + smW, bot); x += smW + gapK
        st[1].r.set(x, top, x + smW, bot); x += smW + gapK
        st[2].r.set(x, top, x + smW, bot); x += smW + gapK
        st[4].r.set(w - sideP - smW, top, w - sideP, bot)
        st[3].r.set(x, top, st[4].r.left - gapK, bot)
        out.addAll(st)
        var y = topP + stripH + gapR
        if (pad) {
            padRect.set(sideP, y, w - sideP, y + 3 * keyH + 2 * gapR)
            if (px == 0f && py == 0f) { px = padRect.centerX(); py = padRect.centerY() }
            y += 3 * keyH + 3 * gapR
            val pr = padRow(); place(pr, y, keyH); out.addAll(pr)
        } else {
            for (row in rows()) { place(row, y, keyH); out.addAll(row); y += keyH + gapR }
        }
        keys = out
    }

    private fun hit(list: List<K>, x: Float, y: Float): K? {
        val m = gapK / 2
        for (kk in list) {
            if (pad && kk.dim) continue
            if (x >= kk.r.left - m && x <= kk.r.right + m && y >= kk.r.top - m && y <= kk.r.bottom + m) return kk
        }
        return null
    }

    // ---------- acoes ----------

    private fun buzz() { if (vib) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    private fun commit(t: String) { svc.currentInputConnection?.commitText(t, 1) }

    private fun capsOk(): Boolean {
        val ei = svc.currentInputEditorInfo ?: return true
        val t = ei.inputType
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }
    }

    /** maiuscula automatica: no comeco do texto e depois de . ! ? ou quebra de linha */
    private fun auto() {
        if (layer != ABC || shift == 2 || !capsOk()) return
        val ic = svc.currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
        shift = if (b.isEmpty() || Regex("([.!?]\\s+|\\n)\$").containsMatchIn(b)) 1 else 0
    }

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

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    /** seleciona a palavra onde o cursor esta (segurar no modo ponteiro) */
    private fun selectWord(): Boolean {
        val ic = svc.currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: return false
        val after = ic.getTextAfterCursor(40, 0)?.toString() ?: return false
        var na = 0
        while (na < before.length && isWord(before[before.length - 1 - na])) na++
        var nb = 0
        while (nb < after.length && isWord(after[nb])) nb++
        if (na + nb == 0) return false
        val req = ExtractedTextRequest()
        req.hintMaxChars = 2000
        val et = ic.getExtractedText(req, 0)
        if (et != null && et.selectionStart >= 0) {
            val cur = et.startOffset + et.selectionStart
            ic.setSelection(cur - na, cur + nb)
        } else {
            val old = sel
            sel = false
            for (i in 0 until na) dpad(KeyEvent.KEYCODE_DPAD_LEFT)
            sel = true
            for (i in 0 until na + nb) dpad(KeyEvent.KEYCODE_DPAD_RIGHT)
            sel = old
        }
        return true
    }

    /** comeca uma selecao a partir do cursor: se ja havia texto marcado, volta o cursor para a ponta e arrasta daqui */
    private fun startSel() {
        val ic = svc.currentInputConnection ?: return
        val req = ExtractedTextRequest()
        req.hintMaxChars = 2000
        val et = ic.getExtractedText(req, 0)
        if (et != null && et.selectionStart >= 0 && et.selectionStart != et.selectionEnd) {
            val end = et.startOffset + et.selectionEnd
            ic.setSelection(end, end)
        }
        sel = true
    }

    private fun press(kk: K) {
        when (kk.type) {
            CH -> {
                commit(if (kk.letter && layer == ABC && shift > 0) kk.label.uppercase() else kk.label)
                if (shift == 1) shift = 0
                auto()
            }
            SHIFT -> {
                val n = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (shift == 1 && n - lastShift < 450) 2 else if (shift > 0) 0 else 1
                lastShift = n
            }
            LAYER -> {
                layer = when (kk.v) { "num" -> NUM; "sym" -> SYM; else -> ABC }
                if (layer == ABC) auto()
            }
            SPACE -> { commit(" "); if (shift == 1) shift = 0; auto() }
            ENTER -> { enter(); auto() }
            PTR -> {
                pad = !pad; sel = false; px = 0f; py = 0f
                if (pad && PointerAccess.inst != null && ov.show()) selMode = false   // seta na tela: arrastar move; segurar seleciona
                else { ov.hide(); if (pad) selMode = true }
            }
            HIDE -> svc.requestHideSelf(0)
            ACT -> when (kk.v) {
                "all" -> menu(android.R.id.selectAll)
                "copy" -> menu(android.R.id.copy)
                "cut" -> menu(android.R.id.cut)
                "paste" -> menu(android.R.id.paste)
                "mode" -> selMode = !selMode
            }
        }
        layoutKeys(); invalidate()
    }

    private fun repeatKey(kk: K) {
        if (kk.type == DEL) { svc.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); auto() }
        else if (kk.type == ARROW) dpad(if (kk.v == "l") KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        invalidate()
    }

    private val repTask = object : Runnable {
        override fun run() {
            val kk = repK
            if (!repRun || kk == null) return
            repeatKey(kk)
            repCount++
            h.postDelayed(this, if (repCount < 2) 420L else 55L)
        }
    }

    /** segurar uma letra com acento abre a janelinha com as variantes */
    private val longTask = Runnable {
        val kk = down ?: return@Runnable
        val s = kk.alt ?: return@Runnable
        val n = s.length
        val pd = 1.4f * cq
        val aw = maxOf(40 * d, 10f * cq)
        val pw = n * aw + (n - 1) * gapK + 2 * pd
        val ph = keyH + 2 * pd
        val left = (kk.r.centerX() - pw / 2).coerceIn(4 * d, maxOf(4 * d, width - pw - 4 * d))
        val top = maxOf(0f, kk.r.top - ph - 6 * d)
        popupRect.set(left, top, left + pw, top + ph)
        val list = ArrayList<K>()
        var x = left + pd
        for (ch in s) {
            val t = if (kk.letter && shift > 0) ch.toString().uppercase() else ch.toString()
            val a = K(t, ALT)
            a.r.set(x, top + pd, x + aw, top + pd + keyH)
            x += aw + gapK
            list.add(a)
        }
        alts = list; down = null; buzz(); invalidate()
    }

    private val padLong = Runnable {
        if (!inPad || padMoved) return@Runnable
        if (scr()) beginScreenSel()
        else if (selectWord()) { sel = true; buzz(); invalidate() }
    }

    // ---------- seta na tela ----------

    private fun scr() = ov.showing

    /** fecha o modo ponteiro e some com a seta (teclado escondido, troca de campo...) */
    fun closePad() {
        if (!pad && !ov.showing) return
        pad = false; sel = false; selecting = false; inPad = false
        PointerAccess.inst?.end()
        ov.hide(); layoutKeys(); invalidate()
    }

    override fun onDetachedFromWindow() { ov.hide(); super.onDetachedFromWindow() }

    private fun beginScreenSel() {
        val a = PointerAccess.inst ?: return
        selecting = a.begin(ov.sx(), ov.sy())
        if (selecting) { sel = true; ov.setSelecting(true); buzz() }
        invalidate()
    }

    private fun extendScreenSel() {
        val n = SystemClock.uptimeMillis()
        if (n - lastExt < 45) return
        lastExt = n
        PointerAccess.inst?.extend(ov.sx(), ov.sy())
    }

    private fun endScreenSel() {
        if (selecting) PointerAccess.inst?.extend(ov.sx(), ov.sy())
        selecting = false; sel = false; ov.setSelecting(false)
        PointerAccess.inst?.end()
    }

    // ---------- toque ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; lastX = x; lastY = y; accX = 0f; accY = 0f; spaceSwipe = false
                if (pad && padRect.contains(x, y)) {
                    inPad = true; padMoved = false; px = x; py = y; buzz()
                    if (scr()) { if (selMode) beginScreenSel() else h.postDelayed(padLong, 300) }
                    else if (selMode) startSel() else h.postDelayed(padLong, 320)
                    invalidate(); return true
                }
                val kk = hit(keys, x, y); down = kk
                if (kk != null) {
                    buzz()
                    if (kk.rep) { repK = kk; repRun = true; repCount = 0; h.post(repTask) }
                    else if (kk.alt != null && !pad) h.postDelayed(longTask, 380)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (inPad) {
                    px = x; py = y
                    if (!padMoved && Math.hypot((x - downX).toDouble(), (y - downY).toDouble()) > 12 * d) { padMoved = true; h.removeCallbacks(padLong) }
                    if (scr()) {
                        ov.moveBy((x - lastX) * speed, (y - lastY) * speed)
                        if (selecting) extendScreenSel()
                    } else {
                        accX += x - lastX; accY += y - lastY
                        val st = 20 * d / speed
                        while (accX >= st) { dpad(KeyEvent.KEYCODE_DPAD_RIGHT); accX -= st }
                        while (accX <= -st) { dpad(KeyEvent.KEYCODE_DPAD_LEFT); accX += st }
                        while (accY >= st) { dpad(KeyEvent.KEYCODE_DPAD_DOWN); accY -= st }
                        while (accY <= -st) { dpad(KeyEvent.KEYCODE_DPAD_UP); accY += st }
                    }
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
                } else if (down != null && !hitStill(x, y)) {
                    // o dedo saiu da tecla: cancela
                    h.removeCallbacks(longTask)
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                h.removeCallbacks(longTask); h.removeCallbacks(repTask); h.removeCallbacks(padLong); repRun = false
                val up = e.actionMasked == MotionEvent.ACTION_UP
                if (inPad) { inPad = false; endScreenSel(); sel = false; invalidate(); return true }
                if (alts.isNotEmpty()) {
                    val s = down
                    if (up && s != null && s.type == ALT) {
                        commit(s.v)
                        if (shift == 1) shift = 0
                        auto()
                    }
                    alts = emptyList(); down = null; invalidate(); return true
                }
                val kk = down; down = null
                if (kk != null && up && !kk.rep && !(kk.type == SPACE && spaceSwipe) && hit(listOf(kk), x, y) != null) press(kk)
                spaceSwipe = false; invalidate()
            }
        }
        return true
    }

    private fun hitStill(x: Float, y: Float): Boolean {
        val kk = down ?: return false
        return hit(listOf(kk), x, y) != null
    }

    // ---------- desenho ----------

    private fun mul(c: Int, a: Float): Int {
        val al = (((c ushr 24) and 0xFF) * a).toInt().coerceIn(0, 255)
        return (al shl 24) or (c and 0x00FFFFFF)
    }

    private fun lighten(c: Int): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        fun m(v: Int) = v + ((255 - v) * 0.35f).toInt()
        return (0xFF shl 24) or (m(r) shl 16) or (m(g) shl 8) or m(b)
    }

    override fun onDraw(c: Canvas) {
        drawPanel(c)
        if (pad) drawPad(c)
        for (kk in keys) drawKey(c, kk)
        if (alts.isNotEmpty()) {
            paint.shader = null; paint.style = Paint.Style.FILL; paint.color = 0x59000000
            tmp.set(popupRect); tmp.offset(0f, 4 * d)
            c.drawRoundRect(tmp, 3.4f * cq, 3.4f * cq, paint)
            paint.color = pal.popupBg
            c.drawRoundRect(popupRect, 3.4f * cq, 3.4f * cq, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = 0x80FFFFFF.toInt()
            tmp.set(popupRect); tmp.inset(0.75f * d, 0.75f * d)
            c.drawRoundRect(tmp, 3.4f * cq, 3.4f * cq, paint)
            paint.style = Paint.Style.FILL
            for (a in alts) drawKey(c, a)
        }
    }

    private fun drawPanel(c: Canvas) {
        val w = width.toFloat(); val hh = height.toFloat(); val rad = 5f * cq
        panelPath.reset()
        tmp.set(0f, 0f, w, hh + rad)
        panelPath.addRoundRect(tmp, rad, rad, Path.Direction.CW)
        paint.style = Paint.Style.FILL; paint.shader = panelShader
        paint.color = 0xFFFFFFFF.toInt()
        c.drawPath(panelPath, paint)
        paint.shader = null
        c.save()
        c.clipRect(0f, 0f, w, rad + 4 * d)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = pal.edge
        c.drawPath(panelPath, paint)
        c.restore()
        paint.style = Paint.Style.FILL
    }

    private fun drawPad(c: Canvas) {
        val rad = 2.4f * cq
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = pal.spBg
        c.drawRoundRect(padRect, rad, rad, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * d; paint.color = pal.border
        tmp.set(padRect); tmp.inset(0.6f * d, 0.6f * d)
        c.drawRoundRect(tmp, rad, rad, paint)
        paint.style = Paint.Style.FILL
        paint.color = mul(pal.txt, 0.7f); paint.textAlign = Paint.Align.CENTER; paint.typeface = tfKey
        paint.textSize = maxOf(14 * d, 3.8f * cq)
        val msg = if (scr()) { if (sel) "Selecionando: arraste para marcar o texto" else "Arraste para mover a seta na tela" }
            else if (sel) "Selecionando: arraste para marcar o texto"
            else if (selMode) "Pressione e arraste para selecionar" else "Arraste para mover o cursor"
        val yy = padRect.centerY() - (paint.descent() + paint.ascent()) / 2
        c.drawText(msg, padRect.centerX(), yy - 0.9f * paint.textSize, paint)
        paint.textSize = maxOf(12 * d, 3.2f * cq)
        c.drawText(
            if (scr()) { if (selMode) "toque em Marcar para so mover a seta" else "segure parado e arraste para selecionar" }
            else if (selMode) "toque em Marcar para so mover o cursor" else "segure para selecionar a palavra",
            padRect.centerX(), yy + 0.8f * paint.textSize, paint)
        if (!scr()) {
            val ic = icons[IC_PTR]
            if (ic != null) {
                val s = 6f * cq / 24f
                c.save(); c.translate(px - 4 * s, py - 3 * s); c.scale(s, s)
                paint.style = Paint.Style.FILL; paint.color = if (sel) acc else 0xFFFFFFFF.toInt(); c.drawPath(ic, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f; paint.strokeJoin = Paint.Join.ROUND
                paint.color = 0xFF111111.toInt(); c.drawPath(ic, paint)
                c.restore()
                paint.style = Paint.Style.FILL; paint.strokeJoin = Paint.Join.MITER
            }
        }
    }

    private fun drawIcon(c: Canvas, id: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val p = icons[id] ?: return
        val s = size / 24f
        c.save(); c.translate(cx - size / 2, cy - size / 2); c.scale(s, s)
        paint.shader = null; paint.color = color
        if (id == IC_SH || id == IC_PTR) paint.style = Paint.Style.FILL
        else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (id == IC_BK) 1.8f else if (id == IC_EN) 2f else 2.2f
            paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(p, paint)
        c.restore()
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT; paint.strokeJoin = Paint.Join.MITER
    }

    private fun drawKey(c: Canvas, kk: K) {
        val a = if (pad && kk.dim) 0.5f else 1f
        val pressed = kk === down
        val active = (kk.type == SHIFT && shift > 0) || (kk.type == PTR && pad) || (kk.type == ACT && kk.v == "mode" && selMode)
        val isEnter = kk.type == ENTER
        val rad = if (kk.pill) kk.r.height() / 2 else 2.4f * cq
        val cx = kk.r.centerX(); val cy = kk.r.centerY()
        c.save()
        if (pressed) c.scale(0.93f, 0.93f, cx, cy)

        // sombra
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = mul(0x40000000, a)
        tmp.set(kk.r); tmp.offset(0f, 2 * d)
        c.drawRoundRect(tmp, rad, rad, paint)

        // fundo
        when {
            pressed -> paint.color = if (isEnter) lighten(acc) else pal.pressBg
            isEnter -> paint.color = acc
            active -> paint.color = pal.activeBg
            kk.sp -> paint.color = mul(pal.spBg, a)
            else -> {
                val g = glass
                if (g != null) { mat.setTranslate(0f, kk.r.top); g.setLocalMatrix(mat); paint.shader = g }
                paint.color = 0xFFFFFFFF.toInt()
            }
        }
        c.drawRoundRect(kk.r, rad, rad, paint)
        paint.shader = null

        // borda e brilho de cima
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * d
        paint.color = if (isEnter) 0x80FFFFFF.toInt() else mul(pal.border, a)
        tmp.set(kk.r); tmp.inset(0.6f * d, 0.6f * d)
        c.drawRoundRect(tmp, rad, rad, paint)
        if (!kk.sp && !pressed) {
            paint.strokeWidth = 1f * d; paint.color = pal.hi
            c.drawLine(kk.r.left + rad, kk.r.top + 1.6f * d, kk.r.right - rad, kk.r.top + 1.6f * d, paint)
        }
        paint.style = Paint.Style.FILL

        // marca de shift travado
        if (kk.type == SHIFT && shift == 2) {
            val bw = 3f * cq
            paint.color = acc
            tmp.set(cx - bw / 2, kk.r.bottom - 1.9f * cq, cx + bw / 2, kk.r.bottom - 1.2f * cq)
            c.drawRoundRect(tmp, 2 * d, 2 * d, paint)
        }

        // conteudo
        val fg = when {
            pressed -> if (isEnter) 0xFFFFFFFF.toInt() else pal.pressTxt
            active -> pal.activeTxt
            isEnter -> 0xFFFFFFFF.toInt()
            kk.type == SPACE -> pal.spaceTxt
            else -> mul(pal.txt, a)
        }
        if (kk.icon != 0) {
            drawIcon(c, kk.icon, cx, cy, 5.8f * cq, fg)
        } else {
            paint.color = fg; paint.textAlign = Paint.Align.CENTER
            if (kk.type == SPACE) { paint.typeface = tfKey; paint.textSize = maxOf(13 * d, 3.6f * cq) }
            else if (kk.pill) { paint.typeface = tfBold; paint.textSize = maxOf(14 * d, 3.8f * cq) }
            else if (kk.sp) { paint.typeface = tfKey; paint.textSize = maxOf(14 * d, 3.8f * cq) }
            else { paint.typeface = tfKey; paint.textSize = maxOf(17 * d, 5.2f * cq) }
            val t = if (kk.letter && layer == ABC && shift > 0) kk.label.uppercase() else kk.label
            c.drawText(t, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
        }
        c.restore()
    }
}
