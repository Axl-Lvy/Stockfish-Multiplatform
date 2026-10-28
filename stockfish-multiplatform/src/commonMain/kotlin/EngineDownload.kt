package fr.axl_lvy.stockfish_multiplatform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of the engine binary download.
 *
 * Only the WebAssembly target downloads the engine at runtime, once per page. Other targets bundle
 * it and stay [Done].
 */
public sealed interface EngineDownload {
  /** No [getStockfish] call has started the download yet. */
  public data object NotStarted : EngineDownload

  /**
   * The engine binary is being downloaded.
   *
   * @property receivedBytes bytes received so far
   * @property totalBytes size of the engine binary
   */
  public data class InProgress(val receivedBytes: Long, val totalBytes: Long) : EngineDownload {
    /** Share of the binary received so far, from 0 to 1. */
    public val fraction: Float
      get() = (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
  }

  /** The engine binary is available; creating an engine no longer needs the network. */
  public data object Done : EngineDownload

  /**
   * The download failed. The next [getStockfish] call retries it.
   *
   * @property message the failure reported by the browser
   */
  public data class Failed(val message: String) : EngineDownload
}

internal expect val initialEngineDownload: EngineDownload

internal val engineDownloadState = MutableStateFlow(initialEngineDownload)

/** Progress of the engine binary download, shared by every engine of the process or page. */
public val stockfishDownload: StateFlow<EngineDownload> = engineDownloadState.asStateFlow()
