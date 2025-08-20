package io.github.axl_lvy.stockfish_multiplatform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidStockfishEngineTest {
    private lateinit var engine: StockfishEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Initialize the context holder directly for testing
        initializeStockfishMultiplatform(context)
        engine = createStockfishEngine()
    }

    @Test
    fun testEngineStartsCorrectly() {
        assertTrue(engine.start())
    }

    @Test
    fun testEngineCanExecuteCommands() {
        engine.start()
        engine.sendCommand("uci")
        val response = engine.readAllLines()
        assertTrue(response.isNotEmpty())
        assertTrue(response.any { it.contains("id name Stockfish") })
    }
}