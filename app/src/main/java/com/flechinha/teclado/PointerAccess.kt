package com.flechinha.teclado

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Servico de acessibilidade: descobre o texto que esta embaixo da ponta da seta, marca, e tambem
 * faz o "clique" da seta em botoes e links de qualquer app.
 */
class PointerAccess : AccessibilityService() {
    companion object { @Volatile var inst: PointerAccess? = null }

    /** avisado quando a marcacao some sozinha (a tela rolou) */
    var onClear: (() -> Unit)? = null

    private var node: AccessibilityNodeInfo? = null
    private var txt = ""
    private var virtual = false      // texto que o app nao deixa marcar (mensagens): marcamos e copiamos por conta propria
    private var whole = false        // sem posicao das letras: marca o bloco de texto inteiro
    private var nodeRect: RectF? = null
    private var vText: String? = null
    private var vS = 0
    private var vE = 0
    private var rects: Array<RectF?> = emptyArray()
    private var anchor = 0
    private var lastEnd = -1

    private var margin = 0
    private var bestN: AccessibilityNodeInfo? = null
    private var bestD = 0L
    private var bestA = 0L

    override fun onServiceConnected() {
        super.onServiceConnected(); inst = this
        margin = (48 * resources.displayMetrics.density).toInt()
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e != null && e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && virtual) { clearVirtual(); onClear?.invoke() }
    }
    override fun onInterrupt() { }
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onDestroy() { inst = null; super.onDestroy() }

    // ---------- clique ----------

    /** toque na tela, no ponto onde esta a ponta da seta (alcanca onde o dedo nao chega) */
    fun click(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        return try {
            val p = Path()
            p.moveTo(x.toFloat(), y.toFloat())
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build()
            dispatchGesture(g, null, null)
        } catch (e: Exception) { false }
    }

    // ---------- selecao ----------

    /** comeca a selecao no texto que esta na ponta da seta */
    fun begin(x: Int, y: Int): Boolean {
        end(); clearVirtual()
        val n = findNode(x, y) ?: return false
        val t = textOf(n)
        if (t.isEmpty()) return false
        var r: Array<RectF?> = emptyArray()
        if (!n.text.isNullOrEmpty()) r = loadRects(n, t.length)
        var ok = false
        for (q in r) if (q != null && q.height() > 0f && q.width() > 0f) { ok = true; break }
        if (ok) {
            node = n; rects = r; txt = t
            anchor = indexAt(x, y)
            lastEnd = anchor
            virtual = !n.isEditable
            if (virtual) { vS = anchor; vE = anchor; vText = "" } else sel(anchor, anchor)
            return true
        }
        // o app nao informa onde cada letra esta: marca o bloco de texto inteiro
        if (n.isEditable) { selOn(n, 0, t.length); return true }
        val b = Rect()
        n.getBoundsInScreen(b)
        virtual = true; whole = true; nodeRect = RectF(b)
        vS = 0; vE = t.length; vText = t
        return true
    }

    /** estica a selecao ate a letra que esta agora na ponta da seta */
    fun extend(x: Int, y: Int) {
        if (node == null) return
        val i = indexAt(x, y)
        if (i == lastEnd) return
        lastEnd = i
        if (virtual) {
            vS = minOf(anchor, i).coerceAtMost(txt.length); vE = maxOf(anchor, i).coerceAtMost(txt.length)
            vText = txt.substring(vS, vE)
        } else sel(anchor, i)
    }

    /** soltou o dedo: para de esticar. A marcacao de mensagens continua ate copiar */
    fun end() { node = null; if (!virtual) rects = emptyArray() }

    fun clearVirtual() { virtual = false; whole = false; nodeRect = null; vText = null; vS = 0; vE = 0; rects = emptyArray() }

    /** retangulos do que esta marcado (so para texto de mensagem) */
    fun highlight(): List<RectF> {
        if (!virtual) return emptyList()
        if (whole) { val r = nodeRect ?: return emptyList(); return listOf(r) }
        if (vS >= vE) return emptyList()
        val out = ArrayList<RectF>()
        for (i in vS until minOf(vE, rects.size)) {
            val r = rects[i] ?: continue
            if (r.height() > 0f && r.width() > 0f) out.add(r)
        }
        return out
    }

    /** copia o texto marcado de uma mensagem; false se nao havia marcacao propria */
    fun copyVirtual(): Boolean {
        val t = vText
        if (!virtual || t.isNullOrEmpty()) return false
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("texto", t))
        clearVirtual()
        return true
    }

    private fun sel(a: Int, b: Int) { val n = node ?: return; selOn(n, a, b) }

    private fun selOn(n: AccessibilityNodeInfo, a: Int, b: Int) {
        val args = Bundle()
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, minOf(a, b))
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, maxOf(a, b))
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    // ---------- achar o texto na tela ----------

    private fun textOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()
        if (!t.isNullOrEmpty()) return t
        return n.contentDescription?.toString() ?: ""
    }

    /** procura em todas as janelas (de cima para baixo) o texto mais perto da ponta da seta */
    private fun findNode(x: Int, y: Int): AccessibilityNodeInfo? {
        val roots = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows.sortedByDescending { it.layer }) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val b = Rect()
                w.getBoundsInScreen(b)
                if (!b.contains(x, y)) continue
                val r = w.root ?: continue
                roots.add(r)
            }
        } catch (e: Exception) { }
        rootInActiveWindow?.let { roots.add(it) }
        for (r in roots) {
            val n = pick(r, x, y)
            if (n != null) return n
        }
        return null
    }

    private fun pick(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val b = Rect()
        if (f != null && f.isEditable) {
            f.getBoundsInScreen(b)
            if (b.contains(x, y)) return f
        }
        bestN = null; bestD = Long.MAX_VALUE; bestA = Long.MAX_VALUE
        scan(root, x, y, 0)
        return bestN ?: (if (f != null && f.isEditable) f else null)
    }

    private fun scan(n: AccessibilityNodeInfo, x: Int, y: Int, depth: Int) {
        if (depth > 40) return
        val b = Rect()
        n.getBoundsInScreen(b)
        val inf = Rect(b)
        inf.inset(-margin, -margin)
        if (!inf.contains(x, y) || !n.isVisibleToUser) return
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            scan(c, x, y, depth + 1)
        }
        if (textOf(n).isEmpty()) return
        val dm = resources.displayMetrics
        // descricao de bloco gigante (janela inteira) nao conta como texto
        if (n.text.isNullOrEmpty() && b.width() >= dm.widthPixels * 0.95 && b.height() >= dm.heightPixels * 0.6) return
        val dx = maxOf(b.left - x, 0, x - b.right).toLong()
        val dy = maxOf(b.top - y, 0, y - b.bottom).toLong()
        val d = dx * dx + dy * dy
        val area = b.width().toLong() * b.height()
        if (d < bestD || (d == bestD && area < bestA)) { bestD = d; bestA = area; bestN = n }
    }

    /** posicao de cada letra do texto na tela (Android 8 ou mais novo) */
    private fun loadRects(n: AccessibilityNodeInfo, len: Int): Array<RectF?> {
        if (Build.VERSION.SDK_INT < 26) return emptyArray()
        val total = minOf(len, 3000)
        val out = arrayOfNulls<RectF>(total)
        var s = 0
        while (s < total) {
            val l = minOf(500, total - s)
            val a = Bundle()
            a.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, s)
            a.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, l)
            if (!n.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, a)) break
            @Suppress("DEPRECATION")
            val arr = n.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY) ?: break
            for (i in arr.indices) if (s + i < total) out[s + i] = arr[i] as? RectF
            s += l
        }
        return out
    }

    /** posicao do cursor (entre duas letras) mais perto do ponto da tela */
    private fun indexAt(x: Int, y: Int): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        val fx = x.toFloat(); val fy = y.toFloat()
        for (i in rects.indices) {
            val r = rects[i] ?: continue
            if (r.height() <= 0f) continue
            val dx = fx - fx.coerceIn(r.left, r.right)
            val dy = (fy - fy.coerceIn(r.top, r.bottom)) * 2f
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = i }
        }
        if (best < 0) return 0
        val r = rects[best] ?: return best
        return if (fx > r.centerX()) best + 1 else best
    }
}
