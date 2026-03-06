@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.axl_lvy.stockfish_multiplatform

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

@JsModule("stockfish.wasm")
external fun stockfishFactory(): Promise<JsAny>

external interface StockfishModule : JsAny {
  fun postMessage(command: JsString)
  fun addMessageListener(callback: (JsString) -> Unit)
  fun removeMessageListener(callback: (JsString) -> Unit)
  fun terminate()
}

class WasmStockfishEngine : StockfishEngine {
  private var module: StockfishModule? = null

  override fun start(): Boolean {
    stockfishFactory().then { resolved: JsAny ->
      module = resolved.unsafeCast()
      null
    }
    return true
  }

  override fun sendCommand(command: String) {
    TODO("Not yet implemented")
  }

  override fun readLine(): String? {
    TODO("Not yet implemented")
  }

  override fun readAllLines(): List<String> {
    TODO("Not yet implemented")
  }

  override fun close() {
    module?.terminate()
    module = null
  }
}
