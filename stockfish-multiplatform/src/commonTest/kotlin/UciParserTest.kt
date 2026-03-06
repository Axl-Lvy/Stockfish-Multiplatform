package fr.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class UciParserTest {

  @Test
  fun parseInfoWithAllFields() = runTest {
    val line =
      "info depth 20 seldepth 25 multipv 1 score cp 35 nodes 1234567 nps 500000 time 2469 pv e2e4 e7e5 g1f3"
    val info = UciParser.parseInfo(line)

    info.depth shouldBe 20
    info.selectiveDepth shouldBe 25
    info.multiPV shouldBe 1
    info.score shouldBe Score.Cp(35)
    info.nodes shouldBe 1234567L
    info.nps shouldBe 500000L
    info.time shouldBe 2469L
    info.pv shouldBe listOf("e2e4", "e7e5", "g1f3")
    info.raw shouldBe line
  }

  @Test
  fun parseInfoWithMateScore() = runTest {
    val line = "info depth 15 score mate 3 pv e2e4"
    val info = UciParser.parseInfo(line)

    info.depth shouldBe 15
    info.score shouldBe Score.Mate(3)
    info.pv shouldBe listOf("e2e4")
  }

  @Test
  fun parseInfoWithNegativeMateScore() = runTest {
    val line = "info depth 10 score mate -2 pv e7e5"
    val info = UciParser.parseInfo(line)

    info.score shouldBe Score.Mate(-2)
  }

  @Test
  fun parseInfoWithNegativeCentipawnScore() = runTest {
    val line = "info depth 12 score cp -150 nodes 100 pv d7d5"
    val info = UciParser.parseInfo(line)

    info.score shouldBe Score.Cp(-150)
  }

  @Test
  fun parseInfoWithMinimalFields() = runTest {
    val line = "info depth 1 score cp 0"
    val info = UciParser.parseInfo(line)

    info.depth shouldBe 1
    info.score shouldBe Score.Cp(0)
    info.selectiveDepth shouldBe null
    info.nodes shouldBe null
    info.nps shouldBe null
    info.time shouldBe null
    info.multiPV shouldBe null
    info.pv shouldBe emptyList()
  }

  @Test
  fun parseInfoWithEmptyPv() = runTest {
    val line = "info depth 5 seldepth 5 score cp 20 nodes 100 nps 50000 time 2"
    val info = UciParser.parseInfo(line)

    info.pv shouldBe emptyList()
  }

  @Test
  fun parseBestMoveWithPonder() = runTest {
    val (best, ponder) = UciParser.parseBestMove("bestmove e2e4 ponder e7e5")

    best shouldBe "e2e4"
    ponder shouldBe "e7e5"
  }

  @Test
  fun parseBestMoveWithoutPonder() = runTest {
    val (best, ponder) = UciParser.parseBestMove("bestmove d2d4")

    best shouldBe "d2d4"
    ponder shouldBe null
  }

  @Test
  fun parseBestMoveWithNoneMove() = runTest {
    val (best, ponder) = UciParser.parseBestMove("bestmove (none)")

    best shouldBe "(none)"
    ponder shouldBe null
  }
}
