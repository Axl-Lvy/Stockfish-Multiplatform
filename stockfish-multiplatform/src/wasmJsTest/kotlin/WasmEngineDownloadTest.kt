@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.string.shouldMatch
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

@JsFun(
  """() => {
  globalThis.__fetchCounts = {};
  if (globalThis.__originalFetch) return;
  globalThis.__originalFetch = globalThis.fetch;
  globalThis.fetch = function (input, init) {
    var url = String((input && input.url) || input);
    globalThis.__fetchCounts[url] = (globalThis.__fetchCounts[url] || 0) + 1;
    return globalThis.__originalFetch.call(globalThis, input, init);
  };
}"""
)
private external fun resetFetchCounting()

@JsFun("(url) => globalThis.__fetchCounts[url] || 0")
private external fun fetchCount(url: JsString): Int

class WasmEngineDownloadTest {

  @BeforeTest
  fun countFetches() {
    resetFetchCounting()
  }

  @Test
  fun recreatingAClosedEngineDoesNotDownloadItAgain() =
    runTest(timeout = 5.minutes) {
      getStockfish().close()
      getStockfish().use { engine ->
        engine.setPosition()
        engine.search(depth = 5).bestMove shouldMatch "[a-h][1-8][a-h][1-8].*"
      }

      fetchCount(STOCKFISH_WASM_CDN_URL.toJsString()) shouldBeLessThanOrEqual 1
    }

  @Test
  fun anEngineCancelledWhileStartingDoesNotDownloadItAgain() =
    runTest(timeout = 5.minutes) {
      val cancelled = async(start = CoroutineStart.UNDISPATCHED) { getStockfish() }
      yield()
      cancelled.cancel()

      getStockfish().use { engine ->
        engine.setPosition()
        engine.search(depth = 5).bestMove shouldMatch "[a-h][1-8][a-h][1-8].*"
      }

      fetchCount(STOCKFISH_WASM_CDN_URL.toJsString()) shouldBeLessThanOrEqual 1
    }
}
