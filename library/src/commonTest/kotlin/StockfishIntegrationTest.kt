package io.github.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Ignore
import kotlin.test.Test

private val MOVE_PATTERN = "[a-h][1-8][a-h][1-8].*"

// The native bridge uses dup2 to redirect stdin/stdout which crashes the JVM test runner.
// These tests work on Android (connectedAndroidTest).
// To run on JVM, the native bridge must be fixed to use dedicated file descriptors.
private val engine by lazy { createStockfish() }

@Ignore
class StockfishIntegrationTest {

  @Test
  fun searchFromStartposReturnsAValidMove() {
    engine.setPosition()
    val result = engine.search(depth = 10)

    result.bestMove shouldMatch MOVE_PATTERN
    result.info.shouldNotBeEmpty()
  }

  @Test
  fun searchCollectsInfoLinesWithIncreasingDepth() {
    engine.setPosition()
    val result = engine.search(depth = 8)

    val depths = result.info.mapNotNull { it.depth }
    depths.shouldNotBeEmpty()
    depths.last() shouldBeGreaterThan depths.first()
  }

  @Test
  fun searchFromFenPosition() {
    val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
    engine.setPosition(fen = fen)
    val result = engine.search(depth = 10)

    result.bestMove shouldMatch MOVE_PATTERN
  }

  @Test
  fun searchFromStartposWithMoves() {
    engine.setPosition(moves = listOf("e2e4", "e7e5"))
    val result = engine.search(depth = 10)

    result.bestMove shouldMatch MOVE_PATTERN
  }

  @Test
  fun searchWithMovetime() {
    engine.setPosition()
    val result = engine.search(moveTime = 500)

    result.bestMove shouldMatch MOVE_PATTERN
  }

  @Test
  fun searchWithNodesLimit() {
    engine.setPosition()
    val result = engine.search(nodes = 10000)

    result.bestMove shouldMatch MOVE_PATTERN
  }

  @Test
  fun onInfoCallbackReceivesEveryInfoLine() {
    engine.setPosition()
    val callbackInfos = mutableListOf<SearchInfo>()

    val result = engine.search(depth = 6, onInfo = { callbackInfos.add(it) })

    callbackInfos shouldBe result.info
  }

  @Test
  fun infoLinesContainScores() {
    engine.setPosition()
    val result = engine.search(depth = 8)

    result.info.forEach { it.score shouldNotBe null }
  }

  @Test
  fun setOptionMultiPvProducesMultiplePrincipalVariations() {
    engine.setOption("MultiPV", "3")
    engine.setPosition()
    val result = engine.search(depth = 8)

    val multiPVs = result.info.mapNotNull { it.multiPV }.distinct()
    multiPVs.size shouldBeGreaterThan 1

    engine.setOption("MultiPV", "1")
  }

  @Test
  fun consecutiveSearchesWork() {
    engine.setPosition()
    val result1 = engine.search(depth = 5)

    engine.setPosition(
      fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
    )
    val result2 = engine.search(depth = 5)

    result1.bestMove shouldMatch MOVE_PATTERN
    result2.bestMove shouldMatch MOVE_PATTERN
  }

  @Test
  fun listenerReceivesRawOutputDuringSearch() {
    val received = mutableListOf<String>()
    val listener: (String) -> Unit = { received.add(it) }
    engine.addMessageListener(listener)

    engine.setPosition()
    engine.search(depth = 3)

    received.shouldNotBeEmpty()
    received.last().startsWith("bestmove") shouldBe true

    engine.removeMessageListener(listener)
  }

  @Test
  fun mateInOneIsDetected() {
    engine.setPosition(fen = "6k1/5ppp/8/8/8/8/5PPP/4Q1K1 w - - 0 1")
    val result = engine.search(depth = 10)

    val lastInfo = result.info.last()
    lastInfo.score shouldBe Score.Mate(1)
  }
}
