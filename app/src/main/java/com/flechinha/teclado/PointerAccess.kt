package com.flechinha.teclado

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Servico de acessibilidade: descobre qual letra esta embaixo da ponta da seta e marca o texto dali.
 * Funciona em campos de texto (e em textos selecionaveis) do app que esta na tela.
 */
class PointerAccess : AccessibilityService() {
    companion object { @Volatile var inst: PointerAccess? = null }

    /** avisado quando a marcacao some sozinha (a tela rolou) */
    var onClear: (() -> Unit)? = null

    private var node: AccessibilityNodeInfo? = null
    private var txt = ""
    private var virtual = false      // texto que o app nao deixa marcar (mensagens): marcamos e copiamos por conta propria
    private var vText: String? = null
    private var vS = 0
    private var vE = 0
    private var rects: Array<RectF?> = emptyArray()
    private var anchor = 0
    private var lastEnd = -1

    override fun onServiceConnected() { super.onServiceConnected(); inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e != null && e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && virtual) { clearVirtual(); onClear?.invoke() }
    }
    override fun onInterrupt() { }
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onDestroy() { inst = null; super.onDestroy() }

    /** comeca a selecao na letra que esta na ponta da seta */
    fun begin(x: Int, y: Int): Boolean {
        end(); clearVirtual()
        val root = rootInActiveWindow ?: return false
        val n = pick(root, x, y) ?: return false
        val t = n.text?.toString() ?: return false
        if (t.isEmpty()) return false
        val r = loadRects(n, t.length)
        if (r.isEmpty()) return false
        node = n; rects = r; txt = t
        anchor = indexAt(x, y)
        lastEnd = anchor
        virtual = !n.isEditable
        if (virtual) { vS = anchor; vE = anchor; vText = "" } else sel(anchor, anchor)
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

    fun clearVirtual() { virtual = false; vText = null; vS = 0; vE = 0; rects = emptyArray() }

    /** retangulos das letras marcadas (so para texto de mensagem) */
    fun highlight(): List<RectF> {
        if (!virtual || vS >= vE) return emptyList()
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

    private fun sel(a: Int, b: Int) {
        val n = node ?: return
        val args = Bundle()
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, minOf(a, b))
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, maxOf(a, b))
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    private fun pick(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val b = Rect()
        if (f != null && f.isEditable) {
            f.getBoundsInScreen(b)
            if (b.contains(x, y)) return f
        }
        val d = deepest(root, x, y, 0)
        if (d != null) return d
        return if (f != null && f.isEditable) f else null
    }

    private fun deepest(n: AccessibilityNodeInfo, x: Int, y: Int, depth: Int): AccessibilityNodeInfo? {
        if (depth > 40 || !n.isVisibleToUser) return null
        val b = Rect()
        n.getBoundsInScreen(b)
        if (!b.contains(x, y)) return null
        for (i in n.childCount - 1 downTo 0) {
            val c = n.getChild(i) ?: continue
            val r = deepest(c, x, y, depth + 1)
            if (r != null) return r
        }
        return if ((n.text?.length ?: 0) > 0) n else null
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
