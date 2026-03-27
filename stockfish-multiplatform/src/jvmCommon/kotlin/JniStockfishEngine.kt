package fr.axl_lvy.stockfish_multiplatform

import kotlin.concurrent.Volatile

internal abstract class JniStockfishEngine : RawEngine {
  private external fun startEngine(nnuePath: String?)

  private external fun nativeSendCommand(cmd: String)

  private external fun readOutput(): String

  private external fun destroyEngine()

  protected abstract fun loadNativeLibrary(): String?

  @Volatile private var destroyed = false

  fun init() {
    destroyed = false
    val nnuePath = loadNativeLibrary()
    startEngine(nnuePath)
  }

  override fun send(command: String) {
    if (destroyed) return
    nativeSendCommand(command)
  }

  override suspend fun readLine(): String {
    if (destroyed) return ""
    return readOutput()
  }

  override fun close() {
    if (!destroyed) {
      destroyed = true
      destroyEngine()
    }
  }
}
