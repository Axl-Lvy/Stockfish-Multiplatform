package io.github.axl_lvy.stockfish_multiplatform

abstract class JniStockfishEngine : StockfishEngine {
    private external fun startEngine()
    private external fun nativeSendCommand(cmd: String)
    private external fun readOutput(): String

    protected abstract fun loadNativeLibrary()

    override fun start(): Boolean {
        loadNativeLibrary()
        startEngine()
        return true
    }

    override fun sendCommand(command: String) = nativeSendCommand(command)

    override fun readLine(): String? {
        val out = readOutput()
        return out.ifEmpty { null }
    }

    override fun readAllLines(): List<String> {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readLine() ?: break
            lines.add(line)
            if (line.startsWith("bestmove") || line == "uciok" || line == "readyok") break
        }
        return lines
    }

    override fun close() {
        sendCommand("quit")
    }
}
