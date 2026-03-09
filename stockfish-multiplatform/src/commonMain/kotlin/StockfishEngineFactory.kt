package fr.axl_lvy.stockfish_multiplatform

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal expect suspend fun createStockfishInternal(): StockfishEngine

private val factoryMutex = Mutex()
private var cachedEngine: StockfishEngine? = null

/**
 * Called by [StockfishEngine.close] to eagerly clear the cached reference, so the closed engine can
 * be garbage-collected immediately.
 */
internal fun clearCachedEngine(engine: StockfishEngine) {
  // Compare identity — only clear if it's still the same instance.
  if (cachedEngine === engine) {
    cachedEngine = null
  }
}

/**
 * Returns the singleton [StockfishEngine] instance.
 *
 * The native Stockfish bridge uses global static state — only one engine can exist per process.
 * This function enforces that constraint: subsequent calls return the same instance unless the
 * previous one has been [closed][StockfishEngine.close], in which case a fresh engine is created.
 *
 * Thread-safe: concurrent callers will never create two engines.
 */
suspend fun getStockfish(): StockfishEngine =
  factoryMutex.withLock {
    cachedEngine?.takeUnless { it.isClosed } ?: createStockfishInternal().also { cachedEngine = it }
  }
