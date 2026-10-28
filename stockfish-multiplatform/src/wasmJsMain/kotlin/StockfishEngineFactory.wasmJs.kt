package fr.axl_lvy.stockfish_multiplatform

internal actual suspend fun createStockfishInternal(): StockfishEngine {
  val raw = WasmRawEngine()
  raw.start()
  val engine = StockfishEngine(raw)
  try {
    engine.init()
  } catch (e: Throwable) {
    engine.close()
    throw e
  }
  return engine
}
