package fr.axl_lvy.stockfish_multiplatform

import java.io.File
import kotlin.io.path.createTempDirectory

internal class JvmStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary(): String {
    val osName = System.getProperty("os.name").lowercase()
    val libName =
      when {
        osName.contains("mac") -> "libstockfishjni.dylib"
        osName.contains("win") -> "stockfishjni.dll"
        else -> "libstockfishjni.so"
      }
    val tempDir = createTempDirectory("stockfishjni").toFile()
    tempDir.deleteOnExit()
    val lib = File(tempDir, libName)
    javaClass.getResourceAsStream("/stockfish/$libName")?.use { input ->
      lib.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Native library not found: /stockfish/$libName")

    for (nnue in listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")) {
      val dest = File(tempDir, nnue)
      javaClass.getResourceAsStream("/stockfish/$nnue")?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
      } ?: error("NNUE network not found: /stockfish/$nnue")
      dest.deleteOnExit()
    }

    @Suppress("UnsafeDynamicallyLoadedCode") System.load(lib.absolutePath)
    return tempDir.absolutePath
  }
}
