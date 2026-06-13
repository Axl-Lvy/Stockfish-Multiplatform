@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

@JsFun(
  """async (cdnJsUrl, cdnWasmUrl) => {
  var jsResp = await fetch(cdnJsUrl);
  if (!jsResp.ok) throw new Error('Failed to load Stockfish JS: ' + jsResp.status);
  var wasmResp = await fetch(cdnWasmUrl);
  if (!wasmResp.ok) throw new Error('Failed to load Stockfish WASM: ' + wasmResp.status);
  var jsText = await jsResp.text();
  // Patch 1: parse wasmUrl|jsUrl from hash so pthread workers get the JS blob URL
  jsText = jsText.replace(
    'e=self.location.hash.substr(1).split(","),s=decodeURIComponent(e[0]||location.origin+location.pathname.replace(/\\.js$/i,".wasm"))',
    'e=self.location.hash.substr(1).split(","),function(){var p=(e[0]||"").split("|");s=decodeURIComponent(p[0]||location.origin+location.pathname.replace(/\\.js$/i,".wasm"));gt=p[1]?decodeURIComponent(p[1]):null}()'
  );
  // Patch 2: use gt (JS blob URL) for pthread worker URL instead of self.location
  jsText = jsText.replace(
    'self.location.origin+self.location.pathname+"#"+s+",worker"',
    'gt+"#"+s+",worker"'
  );
  var wasmBlobUrl = URL.createObjectURL(await wasmResp.blob());
  var jsBlobUrl = URL.createObjectURL(new Blob([jsText], {type: 'application/javascript'}));
  return new Worker(jsBlobUrl + '#' + wasmBlobUrl + '|' + jsBlobUrl);
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
  """(worker, callback, onError) => {
  worker.onmessage = (e) => { var d = e.data; if (typeof d === 'string') callback(d); };
  worker.onerror = (e) => { e.preventDefault(); onError(String(e.message || e)); };
}"""
)
private external fun onWorkerMessage(
  worker: JsAny,
  callback: (JsString) -> Unit,
  onError: (JsString) -> Unit,
)

@JsFun("(worker, msg) => worker.postMessage(msg)")
private external fun postToWorker(worker: JsAny, message: JsString)

@JsFun("(worker) => worker.terminate()") private external fun terminateWorker(worker: JsAny)

internal class WasmRawEngine : RawEngine {
  private var worker: JsAny? = null
  private val messageQueue = ArrayDeque<String>()
  private var pendingContinuation: Continuation<String>? = null
  private var closed = false
  // Set when the worker reports an unrecoverable error. Surfaced to the next (or currently parked)
  // readLine() so an in-flight search() fails instead of hanging forever.
  private var failure: Throwable? = null

  suspend fun start() {
    val w = suspendCancellableCoroutine { cont ->
      val promise =
        createStockfishWorkerAsync(
          STOCKFISH_JS_CDN_URL.toJsString(),
          STOCKFISH_WASM_CDN_URL.toJsString(),
        )
      bridgePromise(
        promise,
        resolve = { value -> cont.resume(value) },
        reject = { error -> cont.resumeWithException(RuntimeException(error.toString())) },
      )
    }
    onWorkerMessage(
      w,
      callback = { data: JsString ->
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
      },
      onError = { message: JsString ->
        val error = RuntimeException("Stockfish worker error: $message")
        val cont = pendingContinuation
        if (cont != null) {
          pendingContinuation = null
          cont.resumeWithException(error)
        } else {
          failure = error
        }
      },
    )
    worker = w
  }

  override fun send(command: String) {
    worker?.let { postToWorker(it, command.toJsString()) }
  }

  override suspend fun readLine(): String {
    // Surface a worker error that arrived between reads before draining anything else.
    failure?.let {
      failure = null
      throw it
    }
    if (messageQueue.isNotEmpty()) {
      return messageQueue.removeFirst()
    }
    // Once closed, the worker is gone and no further messages will arrive. Return the empty
    // shutdown sentinel instead of suspending on a continuation that would never be resumed,
    // which would hang any in-flight search() read loop.
    if (closed) {
      return ""
    }
    // Cancellable so a cancelled coroutine clears the dangling continuation. With plain
    // suspendCoroutine the continuation would stay referenced after cancellation and the next
    // worker message would resume an already-completed continuation (IllegalStateException).
    return suspendCancellableCoroutine { cont ->
      pendingContinuation = cont
      cont.invokeOnCancellation {
        if (pendingContinuation === cont) {
          pendingContinuation = null
        }
      }
    }
  }

  override fun close() {
    closed = true
    worker?.let { terminateWorker(it) }
    worker = null
    // Wake a read loop already parked in readLine() so an in-flight search() observes shutdown and
    // terminates, mirroring the JVM/JNI path where destroyEngine() signals the same empty line.
    pendingContinuation?.let {
      pendingContinuation = null
      it.resume("")
    }
  }
}
