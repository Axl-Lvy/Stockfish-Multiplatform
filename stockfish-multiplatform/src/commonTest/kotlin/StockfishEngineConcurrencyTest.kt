package fr.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

private val MOVE_PATTERN = "[a-h][1-8][a-h][1-8].*"

/**
 * Stress tests that reproduce the SIGSEGV caused by the lack of thread-safety in the native JNI
 * layer. When multiple coroutines call setPosition() concurrently from different OS threads, they
 * enter g_engine->set_position() simultaneously with no mutex, corrupting the shared Position
 * object and crashing with SIGSEGV (exit code 139).
 */
class StockfishEngineConcurrencyTest {

  private val positions =
    listOf(
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
      "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
      "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
      "rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3",
      "rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3",
      "rnbqkbnr/pp2pppp/3p4/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4",
      "rnbqkbnr/pp2pppp/3p4/8/3NP3/8/PPP2PPP/RNBQKB1R b KQkq - 0 4",
      "rnbqkb1r/pp2pppp/3p1n2/8/3NP3/8/PPP2PPP/RNBQKB1R w KQkq - 1 5",
      "rnbqkb1r/pp2pppp/3p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R b KQkq - 2 5",
      "rnbqkb1r/1p2pppp/p2p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R w KQkq - 0 6",
    )

  /**
   * Fires many concurrent setPosition() calls from different OS threads. Two threads entering
   * g_engine->set_position() at the same time corrupt the shared Position object → SIGSEGV.
   *
   * No search() calls are involved — this isolates the data race to set_position().
   */
  @Test
  fun concurrentSetPositionShouldNotCrash() =
    runTest(timeout = 60.seconds) {
      val engine = getStockfish()

      withContext(Dispatchers.Default) {
        repeat(50) { batch ->
          // Launch 6 coroutines that all call setPosition at the same time on different threads.
          val jobs =
            (0 until 6).map { j ->
              val idx = (batch * 6 + j) % positions.size
              launch { engine.setPosition(fen = positions[idx]) }
            }
          jobs.joinAll()
        }
      }

      // Prove the engine is still functional after 300 concurrent setPosition calls.
      engine.setPosition()
      val result = engine.search(depth = 5)
      result.bestMove shouldMatch MOVE_PATTERN

      engine.close()
    }

  /**
   * Simulates the real app flow: stop the running search, set a new position, search again. Each
   * cycle is properly serialised (stop + join before the next setPosition), so this should never
   * crash. It serves as a baseline and a regression test for the fix.
   */
  @Test
  fun rapidMoveNavigationShouldNotCrash() =
    runTest(timeout = 120.seconds) {
      val engine = getStockfish()
      engine.setOption("Threads", "4")

      withContext(Dispatchers.Default + SupervisorJob()) {
        var searchJob: Job? = null

        for (fen in positions) {
          searchJob?.let {
            engine.stop()
            it.join()
          }

          searchJob = launch {
            engine.setPosition(fen = fen)
            engine.search(depth = 20)
          }

          delay(100)
        }

        searchJob?.let {
          engine.stop()
          it.join()
        }
      }

      engine.setOption("Threads", "1")
      engine.setPosition()
      val result = engine.search(depth = 5)
      result.bestMove shouldMatch MOVE_PATTERN

      engine.close()
    }
}
