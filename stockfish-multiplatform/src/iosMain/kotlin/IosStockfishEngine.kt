@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlinx.cinterop.toKString
import stockfish.stockfish_destroy
import stockfish.stockfish_init
import stockfish.stockfish_read
import stockfish.stockfish_send

internal class IosStockfishEngine : RawEngine {

  fun init() {
    stockfish_init()
  }

  override fun send(command: String) {
    stockfish_send(command)
  }

  override suspend fun readLine(): String {
    return stockfish_read()?.toKString() ?: ""
  }

  override fun close() {
    stockfish_destroy()
  }
}
