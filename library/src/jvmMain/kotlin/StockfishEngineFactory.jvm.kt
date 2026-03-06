package io.github.axl_lvy.stockfish_multiplatform

actual fun createStockfish(): StockfishEngine {
  val raw = JvmStockfishEngine()
  raw.init()
  return StockfishEngine(raw).also { it.init() }
}
