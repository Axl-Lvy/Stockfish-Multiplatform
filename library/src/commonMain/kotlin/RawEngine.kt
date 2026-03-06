package io.github.axl_lvy.stockfish_multiplatform

/**
 * Minimal raw I/O contract for a UCI engine process.
 * Platform implementations only need to provide this.
 */
internal interface RawEngine : AutoCloseable {
  /** Send a string to the engine's stdin. */
  fun send(command: String)

  /**
   * Read a line from the engine's stdout.
   * Returns an empty string if no output is available yet (non-blocking poll).
   */
  fun readLine(): String
}
