package fr.axl_lvy.stockfish_multiplatform

import java.io.File
import kotlin.io.path.createTempDirectory

internal class JvmStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary(): String {
    val osName = System.getProperty("os.name").lowercase()
    val libName =
      when {
        osName.contains("win") -> "stockfishjni.dll"
        osName.contains("mac") -> "libstockfishjni.dylib"
        else -> "libstockfishjni.so"
      }
    val tempDir = createTempDirectory("stockfishjni").toFile()
    tempDir.deleteOnExit()
    val lib = File(tempDir, libName)
    javaClass.getResourceAsStream("/stockfish/$libName")?.use { it.copyTo(lib.outputStream()) }
      ?: error("Native library not found: /stockfish/$libName")

    for (nnue in listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")) {
      val dest = File(tempDir, nnue)
      javaClass.getResourceAsStream("/stockfish/$nnue")?.use { it.copyTo(dest.outputStream()) }
        ?: error("NNUE network not found: /stockfish/$nnue")
      dest.deleteOnExit()
    }

    @Suppress("UnsafeDynamicallyLoadedCode") System.load(lib.absolutePath)
    return tempDir.absolutePath
  }
}
