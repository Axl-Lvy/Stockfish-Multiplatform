package io.github.axl_lvy.stockfish_multiplatform

actual fun createStockfishEngine(): StockfishEngine {
    return JvmStockfishEngine()
}