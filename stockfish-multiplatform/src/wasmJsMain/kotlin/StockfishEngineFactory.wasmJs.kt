package fr.axl_lvy.stockfish_multiplatform

internal actual suspend fun createStockfishInternal(): StockfishEngine {
  val raw = WasmRawEngine()
  raw.start()
  return StockfishEngine(raw).also { it.init() }
}
