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

internal class WasmRawEngine : RawEngine {
  var module: StockfishModule? = null

  fun init() {
    stockfishFactory().then { resolved: JsAny ->
      module = resolved.unsafeCast()
      null
    }
  }

  override fun send(command: String) {
    module?.postMessage(command.toJsString())
  }

  override fun readLine(): String {
    // WASM engine is async/callback-based — blocking read is not possible.
    return ""
  }

  override fun close() {
    module?.terminate()
    module = null
  }
}
