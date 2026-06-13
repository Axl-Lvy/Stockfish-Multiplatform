@file:OptIn(ExperimentalAtomicApi::class)

package fr.axl_lvy.stockfish_multiplatform

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
 * - **Simple API** — [setPosition], [search], [setOption], [stop]. Covers most use cases.
 * - **Raw API** — [postMessage] / [unsafePostMessage] to send arbitrary UCI strings, and
 *   [addMessageListener] / [removeMessageListener] to observe raw engine output.
 */
public class StockfishEngine internal constructor(private val raw: RawEngine) : AutoCloseable {

  // Copy-on-write snapshot held atomically. The read loop ([readUntil]) iterates an immutable
  // snapshot, so listeners can be added or removed from any thread without corrupting the
  // iteration or throwing ConcurrentModificationException.
  private val listeners = AtomicReference<List<(String) -> Unit>>(emptyList())

  // Read by the search read loop ([readUntil]) and written by [close], which may run on a different
  // thread. Atomic so the close transition is observed promptly and applied exactly once.
  private val closedFlag = AtomicBoolean(false)
  private val engineMutex = Mutex()

  /**
   * Whether this engine has been [closed][close].
   *
   * After close, calling [getStockfish] will create a fresh engine instance.
   */
  public val isClosed: Boolean
    get() = closedFlag.load()

  internal suspend fun init() {
    raw.send("uci")
    readUntil { it == "uciok" }
  }

  private fun checkOpen() {
    check(!closedFlag.load()) { "StockfishEngine is closed" }
  }

  // ── Raw UCI escape hatch ──

  /**
   * Send any UCI command directly to the engine, serialized by the engine mutex.
   *
   * This is the safe variant: it suspends until the mutex is available, preventing concurrent
   * access to the native engine. For fire-and-forget commands that must not wait (e.g. "stop"), use
   * [unsafePostMessage] instead.
   *
   * @param command the raw UCI command string (e.g. `"ucinewgame"`, `"bench 16 1 13"`)
   * @throws IllegalStateException if the engine has been [closed][close]
   */
  public suspend fun postMessage(command: String) {
    engineMutex.withLock {
      checkOpen()
      raw.send(command)
    }
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
  public fun unsafePostMessage(command: String) {
    raw.send(command)
  }

  /**
   * Register a listener that receives every raw output line from the engine.
   *
   * Listeners are invoked **synchronously** on the coroutine that is reading engine output (i.e.
   * inside [search] or [readUntil]). Keep listener bodies fast to avoid blocking the read loop. An
   * exception thrown by a listener is caught and ignored so it cannot abort an in-flight search.
   *
   * Safe to call from any thread, including while a search is running.
   *
   * @param listener callback invoked with each non-empty output line
   */
  public fun addMessageListener(listener: (String) -> Unit) {
    while (true) {
      val current = listeners.load()
      if (listeners.compareAndSet(current, current + listener)) return
    }
  }

  /**
   * Remove a previously registered listener.
   *
   * Safe to call from any thread, including while a search is running.
   *
   * @param listener the same instance passed to [addMessageListener]
   */
  public fun removeMessageListener(listener: (String) -> Unit) {
    while (true) {
      val current = listeners.load()
      if (listener !in current) return
      if (listeners.compareAndSet(current, current - listener)) return
    }
  }

  // ── High-level API ──

  /**
   * Set a UCI option.
   *
   * Serialized by the engine mutex. Common options: `"Threads"`, `"Hash"`, `"MultiPV"`.
   *
   * @param name the UCI option name
   * @param value the value to set
   * @throws IllegalStateException if the engine has been [closed][close]
   */
  public suspend fun setOption(name: String, value: String) {
    engineMutex.withLock {
      checkOpen()
      raw.send("setoption name $name value $value")
    }
  }

  /**
   * Set the board position.
   *
   * Serialized by the engine mutex.
   *
   * @param fen the FEN string, or `null` for the starting position
   * @param moves moves to apply after the position, in UCI notation (e.g. `"e2e4"`, `"e7e5"`)
   * @throws IllegalStateException if the engine has been [closed][close]
   */
  public suspend fun setPosition(fen: String? = null, moves: List<String> = emptyList()) {
    engineMutex.withLock {
      checkOpen()
      val pos = if (fen != null) "fen $fen" else "startpos"
      val movesStr = if (moves.isNotEmpty()) " moves ${moves.joinToString(" ")}" else ""
      raw.send("position $pos$movesStr")
    }
  }

  /**
   * Search the current position.
   *
   * Blocks (suspends) until the engine emits `"bestmove"`. The engine mutex is held for the entire
   * duration, so other mutex-guarded calls ([setPosition], [setOption], [postMessage]) will queue
   * behind a running search.
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
   * @throws IllegalStateException if the engine is [closed][close] before or during the search
   */
  public suspend fun search(
    depth: Int? = null,
    moveTime: Long? = null,
    nodes: Long? = null,
    onInfo: ((SearchInfo) -> Unit)? = null,
  ): SearchResult =
    engineMutex.withLock {
      checkOpen()
      val cmd = buildString {
        append("go")
        depth?.let { append(" depth $it") }
        moveTime?.let { append(" movetime $it") }
        nodes?.let { append(" nodes $it") }
      }
      raw.send(cmd)

      val infos = mutableListOf<SearchInfo>()
      var bestMove: String? = null
      var ponderMove: String? = null

      readUntil { line ->
        when {
          line.startsWith("info ") && ("score cp" in line || "score mate" in line) -> {
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

      // readUntil only exits without a bestmove when [close] interrupts it. Surface that as an
      // error
      // rather than returning a result with an empty bestMove that callers cannot distinguish.
      val finalBestMove = bestMove ?: error("Engine was closed during the search")
      SearchResult(bestMove = finalBestMove, ponderMove = ponderMove, info = infos)
    }

  /**
   * Stop a running search early.
   *
   * Intentionally **not** guarded by the engine mutex so it can be called from a separate coroutine
   * while [search] holds the lock. The in-progress [search] call will still return a [SearchResult]
   * once the engine emits `"bestmove"`.
   */
  public fun stop() {
    raw.send("stop")
  }

  /**
   * Shut down the engine and release all native resources. Idempotent — subsequent calls are
   * no-ops.
   *
   * After close, [isClosed] returns `true` and the next call to [getStockfish] will create a fresh
   * engine instance.
   */
  override fun close() {
    // compareAndSet makes close exactly-once even under concurrent calls: only the first caller
    // tears the engine down. Setting the flag first means destroying the native engine (which wakes
    // any thread parked in [readUntil] with an empty line) is observed by the loop as "shut down,
    // stop reading" rather than busy-spinning on the blank line forever.
    if (closedFlag.compareAndSet(false, true)) {
      raw.send("quit")
      raw.close()
      clearCachedEngine(this)
    }
  }

  private suspend fun readUntil(predicate: (String) -> Boolean) {
    while (true) {
      val line = raw.readLine()
      if (line.isEmpty()) {
        // The native layer returns an empty line only once it has been shut down. If [close] has
        // been called, end the loop so an in-flight [search] returns instead of spinning; otherwise
        // ignore the blank line and keep reading.
        if (closedFlag.load()) break
        continue
      }
      // Iterate an immutable snapshot so concurrent add/remove cannot disturb the loop, and isolate
      // each listener so a misbehaving one cannot abort the read loop (and the in-flight search).
      for (listener in listeners.load()) {
        try {
          listener(line)
        } catch (_: Throwable) {
          // Intentionally swallowed: listener faults must not break the engine.
        }
      }
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
public data class SearchResult(
  val bestMove: String,
  val ponderMove: String?,
  val info: List<SearchInfo>,
)

/**
 * A single `"info"` line emitted by the engine during search.
 *
 * @property depth the search depth reached, or `null` if not reported
 * @property selectiveDepth the selective (quiescence) depth, or `null`
 * @property score the evaluation from the side to move's perspective, or `null`
 * @property bound qualifies [score] as a [lower][Bound.LOWER] or [upper][Bound.UPPER] bound during
 *   aspiration-window searches, or `null` when the score is exact
 * @property nodes number of nodes searched, or `null`
 * @property nps nodes per second, or `null`
 * @property hashfull transposition table fill in permille (0..1000), or `null`
 * @property tbHits number of tablebase hits, or `null`
 * @property time search time in milliseconds, or `null`
 * @property multiPV the Multi-PV index (1-based), or `null` if single PV
 * @property wdl win/draw/loss estimate in permille, or `null` if not reported
 * @property pv the principal variation as a list of UCI moves
 * @property raw the original unparsed info line
 */
public data class SearchInfo(
  val depth: Int?,
  val selectiveDepth: Int?,
  val score: Score?,
  val nodes: Long?,
  val nps: Long?,
  val time: Long?,
  val multiPV: Int?,
  val pv: List<String>,
  val raw: String,
  val bound: Bound? = null,
  val hashfull: Int? = null,
  val tbHits: Long? = null,
  val wdl: Wdl? = null,
)

/** Qualifies a [Score] that is not exact, as reported by aspiration-window searches. */
public enum class Bound {
  /** The true score is at least the reported value. */
  LOWER,
  /** The true score is at most the reported value. */
  UPPER,
}

/**
 * Win/draw/loss probabilities for the side to move, each in permille (0..1000).
 *
 * @property win probability of winning, in permille
 * @property draw probability of drawing, in permille
 * @property loss probability of losing, in permille
 */
public data class Wdl(val win: Int, val draw: Int, val loss: Int)

/** Engine evaluation score from the side to move's perspective. */
public sealed interface Score {
  /**
   * Centipawn evaluation.
   *
   * @property centipawns the evaluation in centipawns (positive = side to move is better)
   */
  public data class Cp(val centipawns: Int) : Score

  /**
   * Forced mate.
   *
   * @property moves mate distance in half-moves (positive = engine mates, negative = engine gets
   *   mated)
   */
  public data class Mate(val moves: Int) : Score
}
