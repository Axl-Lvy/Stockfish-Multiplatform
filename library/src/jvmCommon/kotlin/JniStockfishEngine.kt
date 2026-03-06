package io.github.axl_lvy.stockfish_multiplatform

internal abstract class JniStockfishEngine : RawEngine {
  private external fun startEngine()

  private external fun nativeSendCommand(cmd: String)

  private external fun readOutput(): String

  protected abstract fun loadNativeLibrary()

  fun init() {
    loadNativeLibrary()
    startEngine()
  }

  override fun send(command: String) = nativeSendCommand(command)

  override fun readLine(): String = readOutput()

  override fun close() {}
}
