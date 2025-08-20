package io.github.axl_lvy.stockfish_multiplatform

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TestStockfishEngine {
    private lateinit var engine: StockfishEngine

    @BeforeTest
    fun setup() {
        engine = createStockfishEngine()
    }

    @Test
    fun `test can start engine`() {
        assertTrue { engine.start() }
    }
}