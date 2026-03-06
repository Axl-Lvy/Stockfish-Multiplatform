package fr.axl_lvy.stockfish_multiplatform

class StockfishEngine internal constructor(private val raw: RawEngine) : AutoCloseable {

  private val listeners = mutableListOf<(String) -> Unit>()
  private var closed = false

  internal suspend fun init() {
    raw.send("uci")
    readUntil { it == "uciok" }
  }

  // ── Raw UCI escape hatch ──

  /** Send any UCI command directly to the engine. */
  fun postMessage(command: String) {
    raw.send(command)
  }

  /** Register a listener for raw engine output lines. */
  fun addMessageListener(listener: (String) -> Unit) {
    listeners.add(listener)
  }

  /** Remove a previously registered listener. */
  fun removeMessageListener(listener: (String) -> Unit) {
    listeners.remove(listener)
  }

  // ── High-level API ──

  /** Set a UCI option (e.g. "Threads", "Hash", "MultiPV"). */
  fun setOption(name: String, value: String) {
    raw.send("setoption name $name value $value")
  }

  /**
   * Set position from a FEN string, optionally followed by moves in UCI notation.
   *
   * @param fen the FEN string, or null for the starting position
   * @param moves moves to apply after the position, in UCI notation (e.g. "e2e4", "e7e5")
   */
  fun setPosition(fen: String? = null, moves: List<String> = emptyList()) {
    val pos = if (fen != null) "fen $fen" else "startpos"
    val movesStr = if (moves.isNotEmpty()) " moves ${moves.joinToString(" ")}" else ""
    raw.send("position $pos$movesStr")
  }

  /**
   * Search the current position.
   *
   * Blocks until the engine emits "bestmove". At least one limit should be set, otherwise the
   * engine searches indefinitely (until [stop] is called from another thread).
   *
   * @param depth max depth to search (null = unlimited)
   * @param moveTime time to search in milliseconds (null = unlimited)
   * @param nodes max nodes to search (null = unlimited)
   * @param onInfo called for each info line during search
   * @return the final result
   */
  suspend fun search(
    depth: Int? = null,
    moveTime: Long? = null,
    nodes: Long? = null,
    onInfo: ((SearchInfo) -> Unit)? = null,
  ): SearchResult {
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

    return SearchResult(bestMove = bestMove, ponderMove = ponderMove, info = infos)
  }

  /** Stop a running search early. The in-progress [search] call will still return a result. */
  fun stop() {
    raw.send("stop")
  }

  /** Shuts down the engine and releases all resources. */
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

/** The final result of a search. */
data class SearchResult(val bestMove: String, val ponderMove: String?, val info: List<SearchInfo>)

/** A single "info" line emitted by the engine during search. */
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

/** Engine score from the side to move's perspective. */
sealed interface Score {
  /** Centipawn evaluation. */
  data class Cp(val centipawns: Int) : Score

  /** Forced mate in [moves] half-moves (positive = engine mates, negative = engine gets mated). */
  data class Mate(val moves: Int) : Score
}
