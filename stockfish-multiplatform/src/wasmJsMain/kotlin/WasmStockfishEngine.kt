@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop

@JsFun(
  """async (cdnJsUrl, cdnWasmUrl) => {
  try {
    var m = import.meta.url;
    var base = m.substring(0, m.lastIndexOf('/') + 1);
    var localJs = base + 'stockfish/stockfish-18.js';
    var localWasm = base + 'stockfish/stockfish-18.wasm';
    var resp = await fetch(localJs, { method: 'HEAD' });
    if (resp.ok) {
      return new Worker(localJs + '#' + localWasm);
    }
  } catch(e) {}
  var jsResp = await fetch(cdnJsUrl);
  if (!jsResp.ok) throw new Error('Failed to load Stockfish: ' + jsResp.status);
  var jsBlob = await jsResp.blob();
  return new Worker(URL.createObjectURL(jsBlob) + '#' + cdnWasmUrl);
}"""
)
private external fun createStockfishWorkerAsync(cdnJsUrl: JsString, cdnWasmUrl: JsString): JsAny

@JsFun("(promise, resolve, reject) => promise.then(v => resolve(v), e => reject(String(e)))")
private external fun bridgePromise(
  promise: JsAny,
  resolve: (JsAny) -> Unit,
  reject: (JsString) -> Unit,
)

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

  suspend fun start() {
    val w =
      suspendCoroutine<JsAny> { cont ->
        val promise =
          createStockfishWorkerAsync(
            STOCKFISH_JS_CDN_URL.toJsString(),
            STOCKFISH_WASM_CDN_URL.toJsString(),
          )
        bridgePromise(
          promise,
          resolve = { value -> cont.resume(value) },
          reject = { error ->
            cont.resumeWithException(RuntimeException(error.toString()))
          },
        )
      }
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
