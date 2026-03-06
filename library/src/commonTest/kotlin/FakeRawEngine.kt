package io.github.axl_lvy.stockfish_multiplatform

/**
 * A fake [RawEngine] that replays scripted output lines and records sent commands.
 * Used to test [StockfishEngine] without a real Stockfish process.
 */
internal class FakeRawEngine(private val responses: ArrayDeque<String> = ArrayDeque()) : RawEngine {
  val sentCommands = mutableListOf<String>()
  var closed = false
    private set

  fun enqueue(vararg lines: String) {
    lines.forEach { responses.addLast(it) }
  }

  override fun send(command: String) {
    sentCommands.add(command)
  }

  override fun readLine(): String {
    return if (responses.isNotEmpty()) responses.removeFirst() else ""
  }

  override fun close() {
    closed = true
  }
}
