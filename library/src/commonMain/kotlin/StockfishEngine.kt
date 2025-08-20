package io.github.axl_lvy.stockfish_multiplatform

interface StockfishEngine {
    /**
     * Starts the Stockfish engine process
     * @return true if engine started successfully
     */
    fun start(): Boolean

    /**
     * Sends a command to the engine
     * @param command The UCI command to send
     */
    fun sendCommand(command: String)

    /**
     * Reads a line from the engine
     * @return The response string or null if no response
     */
    fun readLine(): String?

    /**
     * Reads all available lines until engine is idle
     * @return List of response lines
     */
    fun readAllLines(): List<String>

    /**
     * Stops the engine and releases resources
     */
    fun close()
}
