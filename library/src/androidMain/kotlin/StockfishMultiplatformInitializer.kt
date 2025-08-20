package io.github.axl_lvy.stockfish_multiplatform

import android.content.Context
import androidx.startup.Initializer

internal var applicationContext: Context? = null
    private set

object StockfishMultiplatformContext

class StockfishMultiplatformInitializer : Initializer<StockfishMultiplatformContext> {
    override fun create(context: Context): StockfishMultiplatformContext {
        applicationContext = context.applicationContext
        return StockfishMultiplatformContext
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}

fun initializeStockfishMultiplatform(context: Context) {
    applicationContext = context.applicationContext
}
