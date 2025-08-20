package io.github.axl_lvy.stockfish_multiplatform

actual fun createStockfishEngine(): StockfishEngine {
    val context = applicationContext
    checkNotNull(context) { "StockfishMultiplatformInitializer must be initialized before creating StockfishEngine" }
    return AndroidStockfishEngine(context)
}
