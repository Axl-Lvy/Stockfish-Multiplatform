package fr.axl_lvy.stockfish_multiplatform

internal actual suspend fun createStockfishInternal(): StockfishEngine {
  val raw = AndroidStockfishEngine()
  raw.init()
  return StockfishEngine(raw).also { it.init() }
}
