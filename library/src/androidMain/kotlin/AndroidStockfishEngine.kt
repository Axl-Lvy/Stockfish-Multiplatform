package io.github.axl_lvy.stockfish_multiplatform

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

internal class AndroidStockfishEngine(private val context: Context) : StockfishEngine {
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
        val abi = getDeviceAbi()

        // Determine the correct executable name
        val executableName = when (abi) {
            PossibleAbi.V8 -> "stockfish-android-armv8"
            PossibleAbi.V7 -> "stockfish-android-armv7"
        }

        val resourcePath = "/stockfish/$executableName"

        // Create a file in the app's cache directory
        val cacheDir = context.cacheDir
        val executable = File(cacheDir, executableName)

        // Extract the executable from resources
        javaClass.getResourceAsStream(resourcePath)?.use { input ->
            executable.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not find Stockfish executable at $resourcePath")

        println("Extracted Stockfish to: ${executable.absolutePath} (${executable.length()} bytes)")

        // Set the executable bit
        if (!executable.setExecutable(true)) {
            error("Unable to set ${executable.path} executable")
        }

        return executable
    }

    private fun getDeviceAbi(): PossibleAbi {
        return try {
            val supportedAbis = android.os.Build.SUPPORTED_ABIS
            when {
                supportedAbis.contains("arm64-v8a") -> PossibleAbi.V8
                supportedAbis.contains("armeabi-v7a") -> PossibleAbi.V7
                else -> error("No supported ABI found: ${supportedAbis.joinToString(", ")}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback in case of issues
            PossibleAbi.V8
        }
    }
}

private enum class PossibleAbi {
    V8, V7
}
