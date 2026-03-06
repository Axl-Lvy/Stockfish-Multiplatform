package io.github.axl_lvy.stockfish_multiplatform

internal class AndroidStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary() {
    System.loadLibrary("stockfishjni")
  }
}
