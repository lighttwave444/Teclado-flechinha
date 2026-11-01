package com.flechinha.teclado

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) { super.onCreate(s) }

    override fun onResume() { super.onResume(); build() }

    private fun build() {
        val p = Cfg.pal(this); val acc = Cfg.acc(this); val d = resources.displayMetrics.density
        val pad = (16 * d).toInt(); val c = Cfg.p(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad * 2) }
        fun title(t: String) = TextView(this).apply {
            text = t; setTextColor(p.txt); textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, pad, 0, pad / 2)
        }
        fun button(t: String, on: Boolean, f: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setTextColor(if (on) 0xFFFFFFFF.toInt() else p.txt)
            setBackgroundColor(if (on) acc else p.key); setOnClickListener { f() }
        }
        fun <T> row(name: String, opts: List<Pair<T, String>>, cur: T, set: (T) -> Unit) {
            root.addView(title(name))
            val r = LinearLayout(this)
            for ((v, l) in opts) {
                val b = button(l, v == cur) { set(v); build() }
                r.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(r)
        }
        root.addView(title("Teclado Flechinha"))
        root.addView(button("1. Ativar o teclado", false) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(button("2. Escolher como teclado atual", false) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(title("Seta que anda pela tela"))
        root.addView(button("3. Permitir sobreposição" + (if (PointerOverlay.can(this)) "  ✓" else ""), false) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("4. Ativar acessibilidade (Ponteiro)" + (if (PointerAccess.inst != null) "  ✓" else ""), false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("5. Como pôr o ícone Seta no painel de atalhos", false) {
            android.widget.Toast.makeText(this, "Puxe o painel de atalhos, toque em editar (lápis) e arraste o ícone Seta para cima. Depois é só tocar nele para ligar e desligar a seta na tela toda.", android.widget.Toast.LENGTH_LONG).show()
        })
        row("Aparência", listOf("dark" to "Escuro", "light" to "Claro", "auto" to "Automático"), Cfg.mode(this)) { c.edit().putString("mode", it).apply() }
        root.addView(title("Cor de destaque"))
        val cr = LinearLayout(this)
        for (col in Cfg.ACC) {
            val b = Button(this).apply {
                text = if (col == acc) "●" else ""; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(col)
                setOnClickListener { c.edit().putInt("acc", col).apply(); build() }
            }
            cr.addView(b, LinearLayout.LayoutParams(0, (44 * d).toInt(), 1f))
        }
        root.addView(cr)
        row("Velocidade do ponteiro", listOf(1f to "Lenta", 2f to "Normal", 3f to "Rápida", 4.5f to "Turbo"), Cfg.speed(this)) { c.edit().putFloat("speed", it).apply() }
        row("Altura das teclas", listOf(0.85f to "Baixa", 1f to "Normal", 1.15f to "Alta"), Cfg.hs(this)) { c.edit().putFloat("hs", it).apply() }
        row("Vibração", listOf(true to "Ligada", false to "Desligada"), Cfg.vib(this)) { c.edit().putBoolean("vib", it).apply() }
        root.addView(title("Teste aqui"))
        root.addView(EditText(this).apply {
            hint = "Toque aqui para testar o teclado"; setTextColor(p.txt); setHintTextColor(0x88888888.toInt()); minLines = 3
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(p.bg); addView(root) })
    }
}
