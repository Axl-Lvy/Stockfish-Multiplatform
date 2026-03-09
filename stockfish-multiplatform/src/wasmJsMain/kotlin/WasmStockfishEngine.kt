@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop

@JsFun(
  """() => {
  var jsUrl = new URL('../../../node_modules/stockfish-multiplatform-wasm-js/stockfish/stockfish-18.js', import.meta.url);
  var wasmUrl = new URL('../../../node_modules/stockfish-multiplatform-wasm-js/stockfish/stockfish-18.wasm', import.meta.url);
  return new Worker(jsUrl.href + '#' + wasmUrl.href);
}"""
)
private external fun createStockfishWorker(): JsAny

@JsFun(
  "(worker, callback) => { worker.onmessage = (e) => { var d = e.data; if (typeof d === 'string') callback(d); }; }"
)
private external fun onWorkerMessage(worker: JsAny, callback: (JsString) -> Unit)

@JsFun("(worker, msg) => worker.postMessage(msg)")
private external fun postToWorker(worker: JsAny, message: JsString)

@JsFun("(worker) => worker.terminate()") private external fun terminateWorker(worker: JsAny)

internal class WasmRawEngine : RawEngine {
  private var worker: JsAny? = null
  private val messageQueue = ArrayDeque<String>()
  private var pendingContinuation: Continuation<String>? = null

  fun start() {
    val w = createStockfishWorker()
    onWorkerMessage(w) { data: JsString ->
      val line = data.toString()
      if (line.isNotEmpty()) {
        val cont = pendingContinuation
        if (cont != null) {
          pendingContinuation = null
          cont.resume(line)
        } else {
          messageQueue.addLast(line)
        }
      }
    }
    worker = w
  }

  override fun send(command: String) {
    worker?.let { postToWorker(it, command.toJsString()) }
  }

  override suspend fun readLine(): String {
    if (messageQueue.isNotEmpty()) {
      return messageQueue.removeFirst()
    }
    return suspendCoroutine { cont -> pendingContinuation = cont }
  }

  override fun close() {
    worker?.let { terminateWorker(it) }
    worker = null
  }
}
