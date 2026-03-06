package io.github.axl_lvy.stockfish_multiplatform

import java.io.File
import kotlin.io.path.createTempDirectory

private val NNUE_FILES = listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")

internal class AndroidStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary(): String {
    System.loadLibrary("stockfishjni")
    val context = appContext
    return if (context != null) extractFromAssets(context) else extractFromClasspath()
  }

  private fun extractFromAssets(context: android.content.Context): String {
    val nnueDir = File(context.cacheDir, "stockfish-nnue")
    nnueDir.mkdirs()
    for (nnue in NNUE_FILES) {
      val dest = File(nnueDir, nnue)
      if (!dest.exists()) {
        context.assets.open("stockfish/$nnue").use { input ->
          dest.outputStream().use { output -> input.copyTo(output) }
        }
      }
    }
    return nnueDir.absolutePath
  }

  /** Fallback for Android host tests where no Android context is available. */
  private fun extractFromClasspath(): String {
    val tempDir = createTempDirectory("stockfishjni").toFile()
    tempDir.deleteOnExit()
    for (nnue in NNUE_FILES) {
      val dest = File(tempDir, nnue)
      javaClass.getResourceAsStream("/stockfish/$nnue")?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
      } ?: error("NNUE network not found on classpath: /stockfish/$nnue")
      dest.deleteOnExit()
    }
    return tempDir.absolutePath
  }
}

/** Manual initializer for apps that disable `androidx.startup`. */
fun initializeStockfishMultiplatform(context: android.content.Context) {
  appContext = context.applicationContext
}
