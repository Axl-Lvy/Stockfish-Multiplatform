package io.github.axl_lvy.stockfish_multiplatform

import java.io.File
import kotlin.io.path.createTempDirectory

internal class JvmStockfishEngine : JniStockfishEngine() {
  override fun loadNativeLibrary() {
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

    @Suppress("UnsafeDynamicallyLoadedCode") System.load(lib.absolutePath)
  }
}
