package fr.axl_lvy.stockfish_multiplatform.integration

import fr.axl_lvy.stockfish_multiplatform.createStockfish
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

private val MOVE_PATTERN = "[a-h][1-8][a-h][1-8].*"

class BasicIntegrationTest {

  @Test
  fun engineStartsAndSearches() = runTest {
    val engine = createStockfish()
    engine.setPosition()
    val result = engine.search(depth = 5)

    result.bestMove shouldMatch MOVE_PATTERN
    result.info.shouldNotBeEmpty()
  }

  @Test
  fun engineSearchesFromFen() = runTest {
    val engine = createStockfish()
    val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
    engine.setPosition(fen = fen)
    val result = engine.search(depth = 5)

    result.bestMove shouldMatch MOVE_PATTERN
    result.info.shouldNotBeEmpty()
  }
}
