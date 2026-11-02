package com.flechinha.teclado

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {
    private var kb: KbView? = null
    companion object { @Volatile var visible = false }   // o teclado esta aparecendo agora (a seta usa isso)

    override fun onWindowShown() { super.onWindowShown(); visible = true }

    override fun onCreate() {
        super.onCreate()
        // janela transparente: so o painel de vidro do teclado aparece (com os cantos de cima arredondados)
        try { window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) } catch (e: Exception) { }
    }

    override fun onCreateInputView(): View { val k = KbView(this, this); kb = k; return k }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kb?.reload()
    }

    override fun onFinishInputView(finishingInput: Boolean) { super.onFinishInputView(finishingInput); kb?.closePad() }

    override fun onWindowHidden() { super.onWindowHidden(); visible = false; kb?.closePad() }

    override fun onDestroy() { visible = false; kb?.closePad(); super.onDestroy() }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        kb?.caps()
    }
}
