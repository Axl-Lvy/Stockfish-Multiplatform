package fr.axl_lvy.stockfish_multiplatform

import java.io.File

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
      // Skip only fully extracted files. A zero-length file is the fingerprint of an extraction
      // that
      // was interrupted (process killed, storage full); re-extract it instead of loading a corrupt
      // network. Extraction is atomic: write to a temp file and rename, so `dest` only ever exists
      // once it is complete.
      if (dest.exists() && dest.length() > 0L) {
        continue
      }
      val tmp = File(nnueDir, "$nnue.tmp")
      javaClass.getResourceAsStream("/stockfish/$nnue")?.use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
      } ?: error("NNUE network not found on classpath: /stockfish/$nnue")
      if (!tmp.renameTo(dest)) {
        tmp.copyTo(dest, overwrite = true)
        tmp.delete()
      }
    }
    return nnueDir.absolutePath
  }
}

/** Manual initializer for apps that disable `androidx.startup`. */
public fun initializeStockfishMultiplatform(context: android.content.Context) {
  appContext = context.applicationContext
}
