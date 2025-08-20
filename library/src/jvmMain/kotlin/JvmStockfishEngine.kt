// JvmStockfishEngine.kt
package io.github.axl_lvy.stockfish_multiplatform

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.io.path.createTempDirectory

internal class JvmStockfishEngine : StockfishEngine {
  private var process: Process? = null
  private var input: OutputStreamWriter? = null
  private var output: BufferedReader? = null

  override fun start(): Boolean {
    try {
      val executablePath = extractExecutable()
      val builder = ProcessBuilder(executablePath.absolutePath)
      builder.redirectErrorStream(true)

      process = builder.start()
      input = OutputStreamWriter(process!!.outputStream)
      output = BufferedReader(InputStreamReader(process!!.inputStream))
      return true
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    }
  }

  override fun sendCommand(command: String) {
    input?.apply {
      write("$command\n")
      flush()
    }
  }

  override fun readLine(): String? {
    return output?.readLine()
  }

  override fun readAllLines(): List<String> {
    val lines = mutableListOf<String>()
    var line: String?

    // Wait a bit to collect responses
    Thread.sleep(100)

    // Read available lines
    while (output?.ready() == true) {
      line = output?.readLine()
      if (line != null) {
        lines.add(line)
        // If we see "bestmove", we're done with this command
        if (line.startsWith("bestmove")) {
          break
        }
      }
    }
    return lines
  }

  override fun close() {
    try {
      sendCommand("quit")
      input?.close()
      output?.close()
      process?.destroy()
      process = null
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun extractExecutable(): File {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    println("Extracting Stockfish executable for OS: $osName, Arch: $arch")

    // Determine the correct resource path and executable name
    val executableName = when {
      osName.contains("win") -> "stockfish-windows-x86-64.exe"
      osName.contains("mac") -> "stockfish-macos-x86-64"
      else -> "stockfish-ubuntu-x86-64"
    }
    val resourcePath = "/stockfish/$executableName"

    val tempDir = createTempDirectory("stockfish").toFile()
    tempDir.deleteOnExit()

    val executable = File(tempDir, executableName)

    // Extract the executable from resources
    javaClass.getResourceAsStream(resourcePath)?.use { input ->
      executable.outputStream().use { output ->
        input.copyTo(output)
      }
    } ?: throw IllegalStateException("Could not find Stockfish executable at $resourcePath")

    println("Extracted Stockfish to: ${executable.absolutePath} (${executable.length()} bytes)")

    if (!osName.contains("win")) {
      // On non-Windows systems, we need to set the executable bit
      if (!executable.setExecutable(true)) {
        error("Unable to set ${executable.path} executable")
      }
    }

    return executable
  }
}
