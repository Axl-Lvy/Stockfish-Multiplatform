package io.github.axl_lvy.stockfish_multiplatform

import android.content.Context
import androidx.startup.Initializer

internal var appContext: Context? = null

/** Auto-captures the application context via `androidx.startup`. */
class StockfishMultiplatformInitializer : Initializer<Unit> {
  override fun create(context: Context) {
    appContext = context.applicationContext
  }

  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
