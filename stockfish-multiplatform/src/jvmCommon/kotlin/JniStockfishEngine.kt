package fr.axl_lvy.stockfish_multiplatform

internal abstract class JniStockfishEngine : RawEngine {
  private external fun startEngine(nnuePath: String?)

  private external fun nativeSendCommand(cmd: String)

  private external fun readOutput(): String

  private external fun destroyEngine()

  protected abstract fun loadNativeLibrary(): String?

  fun init() {
    val nnuePath = loadNativeLibrary()
    startEngine(nnuePath)
  }

  override fun send(command: String) = nativeSendCommand(command)

  override suspend fun readLine(): String = readOutput()

  override fun close() {
    destroyEngine()
  }
}
