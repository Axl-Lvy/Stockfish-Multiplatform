@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.toKString
import stockfish.stockfish_destroy
import stockfish.stockfish_init
import stockfish.stockfish_read
import stockfish.stockfish_send

internal class IosStockfishEngine : RawEngine {

  private val destroyed = AtomicInt(0)

  fun init() {
    destroyed.compareAndSet(1, 0)
    stockfish_init()
  }

  override fun send(command: String) {
    if (destroyed.value != 0) return
    stockfish_send(command)
  }

  override suspend fun readLine(): String {
    if (destroyed.value != 0) return ""
    return stockfish_read()?.toKString() ?: ""
  }

  override fun close() {
    if (destroyed.compareAndSet(0, 1)) {
      stockfish_destroy()
    }
  }
}
