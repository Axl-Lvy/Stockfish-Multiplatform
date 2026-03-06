package fr.axl_lvy.stockfish_multiplatform

/**
 * Minimal raw I/O contract for a UCI engine process. Platform implementations only need to provide
 * this.
 */
internal interface RawEngine : AutoCloseable {
  /** Send a string to the engine's stdin. */
  fun send(command: String)

  /** Read a line from the engine's stdout. Suspends until a line is available. */
  suspend fun readLine(): String
}
