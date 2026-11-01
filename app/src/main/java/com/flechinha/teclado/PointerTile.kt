package com.flechinha.teclado

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Icone "Seta" no painel de atalhos: liga e desliga a seta que anda pela tela toda. */
class PointerTile : TileService() {
    override fun onStartListening() { refresh() }

    override fun onClick() {
        val a = PointerAccess.inst
        if (a == null) {
            Toast.makeText(this, "Ligue a acessibilidade do Ponteiro no app Teclado Flechinha", Toast.LENGTH_LONG).show()
        } else {
            a.toggleFree()
        }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (PointerAccess.inst?.freeOn == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
