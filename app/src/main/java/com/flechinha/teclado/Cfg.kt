package com.flechinha.teclado

import android.content.Context
import android.content.res.Configuration

/** Cores do teclado. As quatro primeiras sao usadas pela tela de ajustes; o resto e do visual de vidro do teclado. */
class Pal(
    val bg: Int, val key: Int, val keyAlt: Int, val txt: Int,
    val panelTop: Int, val panelBot: Int, val edge: Int,
    val glassTop: Int, val glassBot: Int, val spBg: Int,
    val border: Int, val hi: Int, val spaceTxt: Int,
    val pressBg: Int, val pressTxt: Int, val activeBg: Int, val activeTxt: Int, val popupBg: Int
)

object Cfg {
    val ACC = listOf(0xFF2F7BFF, 0xFF2FD05A, 0xFF9A4DFF, 0xFFFF4F9A, 0xFFFF8A2A, 0xFFFF4D4D).map { it.toInt() }
    fun p(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun mode(c: Context) = p(c).getString("mode", "dark") ?: "dark"
    fun acc(c: Context) = p(c).getInt("acc", ACC[0])
    fun speed(c: Context) = p(c).getFloat("speed", 2f)
    fun vib(c: Context) = p(c).getBoolean("vib", true)
    fun hs(c: Context) = p(c).getFloat("hs", 1f)
    fun pal(c: Context): Pal {
        val m = mode(c)
        val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val light = m == "light" || (m == "auto" && !night)
        return if (light) Pal(
            bg = 0xFFDDE1EA.toInt(), key = 0xFFFFFFFF.toInt(), keyAlt = 0xFFC3C8D4.toInt(), txt = 0xFF15171D.toInt(),
            panelTop = 0xF5E8EBF3.toInt(), panelBot = 0xF7D9DEE9.toInt(), edge = 0x33000000,
            glassTop = 0xFFFFFFFF.toInt(), glassBot = 0xCCFFFFFF.toInt(), spBg = 0x24000000,
            border = 0x2E000000, hi = 0xCCFFFFFF.toInt(), spaceTxt = 0xFF6B7080.toInt(),
            pressBg = 0xFFB9C0D0.toInt(), pressTxt = 0xFF15171D.toInt(), activeBg = 0xFF15171D.toInt(), activeTxt = 0xFFFFFFFF.toInt(),
            popupBg = 0xF7FFFFFF.toInt()
        ) else Pal(
            bg = 0xFF181A22.toInt(), key = 0xFF2F3242.toInt(), keyAlt = 0xFF232532.toInt(), txt = 0xFFFFFFFF.toInt(),
            panelTop = 0xF2222430.toInt(), panelBot = 0xF710121A.toInt(), edge = 0x73FFFFFF,
            glassTop = 0x57FFFFFF, glassBot = 0x21FFFFFF, spBg = 0x1AFFFFFF,
            border = 0x66FFFFFF, hi = 0x59FFFFFF, spaceTxt = 0xB3FFFFFF.toInt(),
            pressBg = 0x99FFFFFF.toInt(), pressTxt = 0xFF111111.toInt(), activeBg = 0xFFFFFFFF.toInt(), activeTxt = 0xFF111111.toInt(),
            popupBg = 0xF7242632.toInt()
        )
    }
}
