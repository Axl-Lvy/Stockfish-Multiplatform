package io.github.axl_lvy.stockfish_multiplatform

import android.content.Context

internal class AndroidStockfishEngine(private val context: Context) : JniStockfishEngine() {
    override fun loadNativeLibrary() {
        System.loadLibrary("stockfishjni")
    }
}
