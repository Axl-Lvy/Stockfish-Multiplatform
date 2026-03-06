package io.github.axl_lvy.stockfish_multiplatform

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeTrue

val TestStockfishEngine by testSuite {
  val engine = testFixture { createStockfishEngine() } closeWith { close() }

  engine asParameterForEach { test("can start engine") { engine -> engine.start().shouldBeTrue() } }
}
