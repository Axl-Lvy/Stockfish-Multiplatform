package fr.axl_lvy.stockfish_multiplatform

import java.io.File

private val NNUE_FILES = listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")

internal class AndroidStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary(): String {
    System.loadLibrary("stockfishjni")
    return extractNnue()
  }

  private fun extractNnue(): String {
    val context = appContext
    val nnueDir =
      if (context != null) {
        File(context.cacheDir, "stockfish-nnue")
      } else {
        kotlin.io.path.createTempDirectory("stockfishjni").toFile().also { it.deleteOnExit() }
      }
    nnueDir.mkdirs()
    for (nnue in NNUE_FILES) {
      val dest = File(nnueDir, nnue)
      if (!dest.exists()) {
        javaClass.getResourceAsStream("/stockfish/$nnue")?.use { input ->
          dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("NNUE network not found on classpath: /stockfish/$nnue")
      }
    }
    return nnueDir.absolutePath
  }
}

/** Manual initializer for apps that disable `androidx.startup`. */
fun initializeStockfishMultiplatform(context: android.content.Context) {
  appContext = context.applicationContext
}
