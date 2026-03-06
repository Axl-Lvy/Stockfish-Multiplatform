package io.github.axl_lvy.stockfish_multiplatform

actual suspend fun createStockfish(): StockfishEngine {
  val raw = WasmRawEngine()
  raw.start()
  return StockfishEngine(raw).also { it.init() }
}
