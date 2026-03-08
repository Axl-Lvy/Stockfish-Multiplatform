package fr.axl_lvy.stockfish_multiplatform

internal actual suspend fun createStockfishInternal(): StockfishEngine {
  val raw = JvmStockfishEngine()
  raw.init()
  return StockfishEngine(raw).also { it.init() }
}
