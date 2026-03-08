package fr.axl_lvy.stockfish_multiplatform

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kotlin Multiplatform wrapper around the Stockfish UCI chess engine.
 *
 * Obtain an instance via [getStockfish] (singleton — the native layer uses global state, so only
 * one engine may exist per process).
 *
 * ## Thread-safety
 *
 * The high-level API ([setPosition], [search], [setOption], [postMessage]) is serialized by an
 * internal [Mutex] and safe to call from any coroutine. [stop] and [unsafePostMessage] are
 * intentionally **not** guarded so they can interrupt a running search from another
 * coroutine/thread.
 *
 * ## Two API levels
 *
 * - **Simple API** — [setPosition], [search], [setOption], [stop]. Covers most use cases.
 * - **Raw API** — [postMessage] / [unsafePostMessage] to send arbitrary UCI strings, and
 *   [addMessageListener] / [removeMessageListener] to observe raw engine output.
 */
class StockfishEngine internal constructor(private val raw: RawEngine) : AutoCloseable {

  private val listeners = mutableListOf<(String) -> Unit>()
  private var closed = false
  private val engineMutex = Mutex()

  /**
   * Whether this engine has been [closed][close].
   *
   * After close, calling [getStockfish] will create a fresh engine instance.
   */
  val isClosed: Boolean get() = closed

  internal suspend fun init() {
    raw.send("uci")
    readUntil { it == "uciok" }
  }

  // ── Raw UCI escape hatch ──

  /**
   * Send any UCI command directly to the engine, serialized by the engine mutex.
   *
   * This is the safe variant: it suspends until the mutex is available, preventing concurrent
   * access to the native engine. For fire-and-forget commands that must not wait (e.g. "stop"),
   * use [unsafePostMessage] instead.
   *
   * @param command the raw UCI command string (e.g. `"ucinewgame"`, `"bench 16 1 13"`)
   */
  suspend fun postMessage(command: String) {
    engineMutex.withLock { raw.send(command) }
  }

  /**
   * Send any UCI command directly to the engine **without** acquiring the engine mutex.
   *
   * This is the unguarded escape hatch — useful for commands like `"stop"` that must be sent
   * immediately while another coroutine holds the mutex (e.g. during [search]). No thread-safety
   * guarantees are provided; callers are responsible for ensuring correctness.
   *
   * @param command the raw UCI command string
   */
  fun unsafePostMessage(command: String) {
    raw.send(command)
  }

  /**
   * Register a listener that receives every raw output line from the engine.
   *
   * Listeners are invoked **synchronously** on the coroutine that is reading engine output (i.e.
   * inside [search] or [readUntil]). Keep listener bodies fast to avoid blocking the read loop.
   *
   * @param listener callback invoked with each non-empty output line
   */
  fun addMessageListener(listener: (String) -> Unit) {
    listeners.add(listener)
  }

  /**
   * Remove a previously registered listener.
   *
   * @param listener the same instance passed to [addMessageListener]
   */
  fun removeMessageListener(listener: (String) -> Unit) {
    listeners.remove(listener)
  }

  // ── High-level API ──

  /**
   * Set a UCI option.
   *
   * Serialized by the engine mutex. Common options: `"Threads"`, `"Hash"`, `"MultiPV"`.
   *
   * @param name the UCI option name
   * @param value the value to set
   */
  suspend fun setOption(name: String, value: String) {
    engineMutex.withLock { raw.send("setoption name $name value $value") }
  }

  /**
   * Set the board position.
   *
   * Serialized by the engine mutex.
   *
   * @param fen the FEN string, or `null` for the starting position
   * @param moves moves to apply after the position, in UCI notation (e.g. `"e2e4"`, `"e7e5"`)
   */
  suspend fun setPosition(fen: String? = null, moves: List<String> = emptyList()) {
    engineMutex.withLock {
      val pos = if (fen != null) "fen $fen" else "startpos"
      val movesStr = if (moves.isNotEmpty()) " moves ${moves.joinToString(" ")}" else ""
      raw.send("position $pos$movesStr")
    }
  }

  /**
   * Search the current position.
   *
   * Blocks (suspends) until the engine emits `"bestmove"`. The engine mutex is held for the
   * entire duration, so other mutex-guarded calls ([setPosition], [setOption], [postMessage])
   * will queue behind a running search.
   *
   * To cancel a search early, call [stop] from another coroutine — it is intentionally unguarded
   * and will cause the engine to emit `"bestmove"` promptly, releasing the mutex.
   *
   * At least one limit should be set; otherwise the engine searches indefinitely until [stop] is
   * called.
   *
   * @param depth max depth to search (`null` = unlimited)
   * @param moveTime time to search in milliseconds (`null` = unlimited)
   * @param nodes max nodes to search (`null` = unlimited)
   * @param onInfo called for each info line during search
   * @return the final result containing the best move, ponder move, and collected info lines
   */
  suspend fun search(
    depth: Int? = null,
    moveTime: Long? = null,
    nodes: Long? = null,
    onInfo: ((SearchInfo) -> Unit)? = null,
  ): SearchResult = engineMutex.withLock {
    val cmd = buildString {
      append("go")
      depth?.let { append(" depth $it") }
      moveTime?.let { append(" movetime $it") }
      nodes?.let { append(" nodes $it") }
    }
    raw.send(cmd)

    val infos = mutableListOf<SearchInfo>()
    var bestMove = ""
    var ponderMove: String? = null

    readUntil { line ->
      when {
        line.startsWith("info ") && "score" in line -> {
          val info = UciParser.parseInfo(line)
          infos.add(info)
          onInfo?.invoke(info)
        }
        line.startsWith("bestmove") -> {
          val (best, ponder) = UciParser.parseBestMove(line)
          bestMove = best
          ponderMove = ponder
        }
      }
      line.startsWith("bestmove")
    }

    SearchResult(bestMove = bestMove, ponderMove = ponderMove, info = infos)
  }

  /**
   * Stop a running search early.
   *
   * Intentionally **not** guarded by the engine mutex so it can be called from a separate
   * coroutine while [search] holds the lock. The in-progress [search] call will still return a
   * [SearchResult] once the engine emits `"bestmove"`.
   */
  fun stop() {
    raw.send("stop")
  }

  /**
   * Shut down the engine and release all native resources. Idempotent — subsequent calls are
   * no-ops.
   *
   * After close, [isClosed] returns `true` and the next call to [getStockfish] will create a
   * fresh engine instance.
   */
  override fun close() {
    if (!closed) {
      raw.send("quit")
      raw.close()
      closed = true
    }
  }

  private suspend fun readUntil(predicate: (String) -> Boolean) {
    while (true) {
      val line = raw.readLine()
      if (line.isEmpty()) continue
      for (listener in listeners) listener(line)
      if (predicate(line)) break
    }
  }
}

/**
 * The final result of a [search][StockfishEngine.search].
 *
 * @property bestMove the engine's chosen move in UCI notation (e.g. `"e2e4"`)
 * @property ponderMove the expected reply (for pondering), or `null` if not provided
 * @property info all `"info"` lines with scores emitted during the search, in order
 */
data class SearchResult(val bestMove: String, val ponderMove: String?, val info: List<SearchInfo>)

/**
 * A single `"info"` line emitted by the engine during search.
 *
 * @property depth the search depth reached, or `null` if not reported
 * @property selectiveDepth the selective (quiescence) depth, or `null`
 * @property score the evaluation from the side to move's perspective, or `null`
 * @property nodes number of nodes searched, or `null`
 * @property nps nodes per second, or `null`
 * @property time search time in milliseconds, or `null`
 * @property multiPV the Multi-PV index (1-based), or `null` if single PV
 * @property pv the principal variation as a list of UCI moves
 * @property raw the original unparsed info line
 */
data class SearchInfo(
  val depth: Int?,
  val selectiveDepth: Int?,
  val score: Score?,
  val nodes: Long?,
  val nps: Long?,
  val time: Long?,
  val multiPV: Int?,
  val pv: List<String>,
  val raw: String,
)

/**
 * Engine evaluation score from the side to move's perspective.
 */
sealed interface Score {
  /**
   * Centipawn evaluation.
   *
   * @property centipawns the evaluation in centipawns (positive = side to move is better)
   */
  data class Cp(val centipawns: Int) : Score

  /**
   * Forced mate.
   *
   * @property moves mate distance in half-moves (positive = engine mates, negative = engine gets
   *   mated)
   */
  data class Mate(val moves: Int) : Score
}
