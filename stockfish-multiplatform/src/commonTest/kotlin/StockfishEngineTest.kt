package fr.axl_lvy.stockfish_multiplatform

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

private suspend fun createFakeEngine(
  vararg initResponses: String
): Pair<StockfishEngine, FakeRawEngine> {
  val raw = FakeRawEngine()
  raw.enqueue(*initResponses, "uciok")
  val engine = StockfishEngine(raw)
  engine.init()
  return engine to raw
}

class StockfishEngineInitTest {

  @Test
  fun initSendsUciAndWaitsForUciok() = runTest {
    val (_, raw) = createFakeEngine()

    raw.sentCommands shouldContain "uci"
  }

  @Test
  fun initSkipsNonUciokLines() = runTest {
    val (_, raw) =
      createFakeEngine(
        "id name Stockfish 17",
        "id author the Stockfish developers",
        "option name Threads type spin default 1 min 1 max 1024",
      )

    raw.sentCommands shouldBe listOf("uci")
  }
}

class StockfishEngineSetOptionTest {

  @Test
  fun setOptionSendsCorrectUciCommand() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.setOption("Threads", "4")

    raw.sentCommands.last() shouldBe "setoption name Threads value 4"
  }

  @Test
  fun setOptionWithHash() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.setOption("Hash", "256")

    raw.sentCommands.last() shouldBe "setoption name Hash value 256"
  }
}

class StockfishEngineSetPositionTest {

  @Test
  fun setPositionWithNoArgsSendsStartpos() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.setPosition()

    raw.sentCommands.last() shouldBe "position startpos"
  }

  @Test
  fun setPositionWithFen() = runTest {
    val (engine, raw) = createFakeEngine()
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

    engine.setPosition(fen = fen)

    raw.sentCommands.last() shouldBe "position fen $fen"
  }

  @Test
  fun setPositionWithStartposAndMoves() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.setPosition(moves = listOf("e2e4", "e7e5"))

    raw.sentCommands.last() shouldBe "position startpos moves e2e4 e7e5"
  }

  @Test
  fun setPositionWithFenAndMoves() = runTest {
    val (engine, raw) = createFakeEngine()
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

    engine.setPosition(fen = fen, moves = listOf("e7e5"))

    raw.sentCommands.last() shouldBe "position fen $fen moves e7e5"
  }
}

class StockfishEngineSearchTest {

  @Test
  fun searchWithDepth() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue(
      "info depth 1 score cp 20 pv e2e4",
      "info depth 2 score cp 15 pv e2e4 e7e5",
      "bestmove e2e4 ponder e7e5",
    )

    val result = engine.search(depth = 2)

    raw.sentCommands.last() shouldBe "go depth 2"
    result.bestMove shouldBe "e2e4"
    result.ponderMove shouldBe "e7e5"
    result.info shouldHaveSize 2
    result.info[0].depth shouldBe 1
    result.info[1].depth shouldBe 2
  }

  @Test
  fun searchWithMovetime() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("info depth 10 score cp 30 pv d2d4", "bestmove d2d4")

    val result = engine.search(moveTime = 1000)

    raw.sentCommands.last() shouldBe "go movetime 1000"
    result.bestMove shouldBe "d2d4"
    result.ponderMove shouldBe null
  }

  @Test
  fun searchWithNodes() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("info depth 5 score cp 10 pv g1f3", "bestmove g1f3")

    val result = engine.search(nodes = 50000)

    raw.sentCommands.last() shouldBe "go nodes 50000"
  }

  @Test
  fun searchWithMultipleLimits() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("bestmove e2e4")

    engine.search(depth = 20, moveTime = 5000, nodes = 1000000)

    raw.sentCommands.last() shouldBe "go depth 20 movetime 5000 nodes 1000000"
  }

  @Test
  fun searchWithNoLimitsSendsBareGo() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("bestmove e2e4")

    engine.search()

    raw.sentCommands.last() shouldBe "go"
  }

  @Test
  fun searchSkipsInfoLinesWithoutScore() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue(
      "info depth 1 seldepth 1 nodes 20 nps 10000 time 2",
      "info depth 1 score cp 20 pv e2e4",
      "info string this is a debug message",
      "bestmove e2e4",
    )

    val result = engine.search(depth = 1)

    result.info shouldHaveSize 1
    result.info[0].score shouldBe Score.Cp(20)
  }

  @Test
  fun searchOnInfoCallbackIsCalledForEachInfoLine() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue(
      "info depth 1 score cp 20 pv e2e4",
      "info depth 2 score cp 15 pv e2e4 e7e5",
      "info depth 3 score cp 25 pv d2d4",
      "bestmove d2d4",
    )

    val callbackInfos = mutableListOf<SearchInfo>()
    val result = engine.search(depth = 3, onInfo = { callbackInfos.add(it) })

    callbackInfos shouldHaveSize 3
    callbackInfos shouldBe result.info
  }
}

class StockfishEnginePostMessageTest {

  @Test
  fun postMessageSendsRawCommand() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.postMessage("ucinewgame")

    raw.sentCommands.last() shouldBe "ucinewgame"
  }

  @Test
  fun postMessageSendsArbitraryString() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.postMessage("bench 16 1 13")

    raw.sentCommands.last() shouldBe "bench 16 1 13"
  }
}

class StockfishEngineUnsafePostMessageTest {

  @Test
  fun unsafePostMessageSendsRawCommand() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.unsafePostMessage("ucinewgame")

    raw.sentCommands.last() shouldBe "ucinewgame"
  }

  @Test
  fun unsafePostMessageSendsArbitraryString() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.unsafePostMessage("bench 16 1 13")

    raw.sentCommands.last() shouldBe "bench 16 1 13"
  }
}

class StockfishEngineListenerTest {

  @Test
  fun listenerReceivesLinesDuringSearch() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("info depth 1 score cp 20 pv e2e4", "bestmove e2e4")

    val receivedLines = mutableListOf<String>()
    engine.addMessageListener { receivedLines.add(it) }
    engine.search(depth = 1)

    receivedLines shouldHaveSize 2
    receivedLines[0] shouldBe "info depth 1 score cp 20 pv e2e4"
    receivedLines[1] shouldBe "bestmove e2e4"
  }

  @Test
  fun removedListenerStopsReceivingLines() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("bestmove e2e4")

    val receivedLines = mutableListOf<String>()
    val listener: (String) -> Unit = { receivedLines.add(it) }
    engine.addMessageListener(listener)
    engine.removeMessageListener(listener)
    engine.search(depth = 1)

    receivedLines shouldHaveSize 0
  }

  @Test
  fun multipleListenersAllReceiveLines() = runTest {
    val (engine, raw) = createFakeEngine()
    raw.enqueue("bestmove e2e4")

    val lines1 = mutableListOf<String>()
    val lines2 = mutableListOf<String>()
    engine.addMessageListener { lines1.add(it) }
    engine.addMessageListener { lines2.add(it) }
    engine.search(depth = 1)

    lines1 shouldHaveSize 1
    lines2 shouldHaveSize 1
  }
}

class StockfishEngineStopTest {

  @Test
  fun stopSendsStopCommand() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.stop()

    raw.sentCommands.last() shouldBe "stop"
  }
}

class StockfishEngineCloseTest {

  @Test
  fun closeSendsQuitAndClosesRawEngine() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.close()

    raw.sentCommands.last() shouldBe "quit"
    raw.closed shouldBe true
  }

  @Test
  fun closeIsIdempotent() = runTest {
    val (engine, raw) = createFakeEngine()

    engine.close()
    val commandsAfterFirstClose = raw.sentCommands.size
    engine.close()

    raw.sentCommands.size shouldBe commandsAfterFirstClose
  }

  @Test
  fun isClosedReturnsTrueAfterClose() = runTest {
    val (engine, _) = createFakeEngine()

    engine.isClosed shouldBe false
    engine.close()
    engine.isClosed shouldBe true
  }

  @Test
  fun setPositionAfterCloseThrows() = runTest {
    val (engine, _) = createFakeEngine()
    engine.close()

    assertFailsWith<StockfishClosedException> { engine.setPosition() }
  }

  @Test
  fun searchAfterCloseThrows() = runTest {
    val (engine, _) = createFakeEngine()
    engine.close()

    assertFailsWith<StockfishClosedException> { engine.search(depth = 1) }
  }

  @Test
  fun setOptionAfterCloseThrows() = runTest {
    val (engine, _) = createFakeEngine()
    engine.close()

    assertFailsWith<StockfishClosedException> { engine.setOption("Threads", "1") }
  }

  @Test
  fun postMessageAfterCloseThrows() = runTest {
    val (engine, _) = createFakeEngine()
    engine.close()

    assertFailsWith<StockfishClosedException> { engine.postMessage("isready") }
  }

  @Test
  fun unsafePostMessageAfterCloseThrows() = runTest {
    val (engine, _) = createFakeEngine()
    engine.close()

    assertFailsWith<StockfishClosedException> { engine.unsafePostMessage("isready") }
  }

  @Test
  fun stopAfterCloseIsNoOp() = runTest {
    val (engine, raw) = createFakeEngine()
    engine.close()
    val commandsAfterClose = raw.sentCommands.size

    engine.stop() // should not throw

    raw.sentCommands.size shouldBe commandsAfterClose
  }
}
