package io.github.axl_lvy.stockfish_multiplatform

actual fun createStockfish(): StockfishEngine {
  val raw = WasmRawEngine()
  raw.init()
  return StockfishEngine(raw)
}
