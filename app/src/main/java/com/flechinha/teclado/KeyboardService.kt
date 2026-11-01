package com.flechinha.teclado

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {
    private var kb: KbView? = null
    override fun onCreateInputView(): View { val k = KbView(this, this); kb = k; return k }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        kb?.reload()
    }
}
