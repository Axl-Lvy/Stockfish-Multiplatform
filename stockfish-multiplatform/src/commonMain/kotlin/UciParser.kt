package fr.axl_lvy.stockfish_multiplatform

internal object UciParser {

  fun parseInfo(line: String): SearchInfo {
    val tokens = line.split(" ")
    var depth: Int? = null
    var seldepth: Int? = null
    var score: Score? = null
    var nodes: Long? = null
    var nps: Long? = null
    var time: Long? = null
    var multiPV: Int? = null
    var pv: List<String> = emptyList()

    var i = 0
    while (i < tokens.size) {
      when (tokens[i]) {
        "depth" -> depth = tokens.getOrNull(++i)?.toIntOrNull()
        "seldepth" -> seldepth = tokens.getOrNull(++i)?.toIntOrNull()
        "multipv" -> multiPV = tokens.getOrNull(++i)?.toIntOrNull()
        "nodes" -> nodes = tokens.getOrNull(++i)?.toLongOrNull()
        "nps" -> nps = tokens.getOrNull(++i)?.toLongOrNull()
        "time" -> time = tokens.getOrNull(++i)?.toLongOrNull()
        "score" -> {
          val type = tokens.getOrNull(++i)
          val value = tokens.getOrNull(++i)
          score =
            when (type) {
              "cp" -> value?.toIntOrNull()?.let { Score.Cp(it) }
              "mate" -> value?.toIntOrNull()?.let { Score.Mate(it) }
              else -> null
            }
        }
        "pv" -> {
          pv = tokens.subList(i + 1, tokens.size)
          i = tokens.size
        }
      }
      i++
    }

    return SearchInfo(
      depth = depth,
      selectiveDepth = seldepth,
      score = score,
      nodes = nodes,
      nps = nps,
      time = time,
      multiPV = multiPV,
      pv = pv,
      raw = line,
    )
  }

  fun parseBestMove(line: String): Pair<String, String?> {
    val tokens = line.split(" ")
    val best = tokens.getOrElse(1) { "(none)" }
    val ponder = if (tokens.size >= 4 && tokens[2] == "ponder") tokens[3] else null
    return best to ponder
  }
}
