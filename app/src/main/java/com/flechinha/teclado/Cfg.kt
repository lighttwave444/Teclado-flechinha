package com.flechinha.teclado

import android.content.Context
import android.content.res.Configuration

class Pal(val bg: Int, val key: Int, val keyAlt: Int, val txt: Int)

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
        return if (light) Pal(0xFFDDE1EA.toInt(), 0xFFFFFFFF.toInt(), 0xFFC3C8D4.toInt(), 0xFF15171D.toInt())
        else Pal(0xFF181A22.toInt(), 0xFF2F3242.toInt(), 0xFF232532.toInt(), 0xFFFFFFFF.toInt())
    }
}
