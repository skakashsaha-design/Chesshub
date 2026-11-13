package com.example.chess

data class OpeningInfo(
  val eco: String,
  val name: String,
  val movesLine: String,
)

/**
 * Compact curated ECO opening recognizer (< 5 KB in RAM).
 * Matches longest prefix of SAN moves cleanly.
 */
object OpeningBook {

  private data class Entry(
    val tokens: List<String>,
    val eco: String,
    val name: String,
  )

  private val entries: List<Entry> = listOf(
    "e4 e5 Nf3 Nc6 Bb5 a6" to ("C70" to "Ruy Lopez: Morphy Defense"),
    "e4 e5 Nf3 Nc6 Bb5 Nf6" to ("C65" to "Ruy Lopez: Berlin Defense"),
    "e4 e5 Nf3 Nc6 Bb5" to ("C60" to "Ruy Lopez"),
    "e4 e5 Nf3 Nc6 Bc4 Bc5" to ("C50" to "Italian Game: Giuoco Piano"),
    "e4 e5 Nf3 Nc6 Bc4 Nf6" to ("C55" to "Italian Game: Two Knights Defense"),
    "e4 e5 Nf3 Nc6 Bc4" to ("C50" to "Italian Game"),
    "e4 e5 Nf3 Nc6 d4 exd4" to ("C44" to "Scotch Game"),
    "e4 e5 Nf3 Nf6" to ("C42" to "Petrov's Defense"),
    "e4 e5 f4" to ("C30" to "King's Gambit"),
    "e4 e5 Nc3" to ("C25" to "Vienna Game"),
    "e4 e5 Bc4" to ("C23" to "Bishop's Opening"),
    "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6" to ("B90" to "Sicilian Defense: Najdorf"),
    "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 g6" to ("B34" to "Sicilian Defense: Accelerated Dragon"),
    "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6" to ("B70" to "Sicilian Defense: Dragon"),
    "e4 c5 Nf3 e6" to ("B40" to "Sicilian Defense: French Variation"),
    "e4 c5 c3" to ("B22" to "Sicilian Defense: Alapin"),
    "e4 c5" to ("B20" to "Sicilian Defense"),
    "e4 e6 d4 d5 Nc3" to ("C11" to "French Defense: Classical"),
    "e4 e6 d4 d5 e5" to ("C02" to "French Defense: Advance"),
    "e4 e6" to ("C00" to "French Defense"),
    "e4 c6 d4 d5 Nc3" to ("B12" to "Caro-Kann Defense"),
    "e4 c6 d4 d5 e5" to ("B12" to "Caro-Kann: Advance Variation"),
    "e4 c6" to ("B10" to "Caro-Kann Defense"),
    "e4 d5 exd5 Qxd5" to ("B01" to "Scandinavian Defense"),
    "e4 d5" to ("B01" to "Scandinavian Defense"),
    "e4 d6 d4 Nf6 Nc3" to ("B07" to "Pirc Defense"),
    "e4 Nf6" to ("B02" to "Alekhine's Defense"),
    "e4 g6" to ("B06" to "Modern Defense"),
    "d4 d5 c4 e6 Nc3 Nf6" to ("D30" to "Queen's Gambit Declined"),
    "d4 d5 c4 c6" to ("D10" to "Slav Defense"),
    "d4 d5 c4 dxc4" to ("D20" to "Queen's Gambit Accepted"),
    "d4 d5 c4" to ("D06" to "Queen's Gambit"),
    "d4 d5 Bf4" to ("D00" to "London System"),
    "d4 Nf6 c4 g6 Nc3 Bg7" to ("E60" to "King's Indian Defense"),
    "d4 Nf6 c4 e6 Nc3 Bb4" to ("E20" to "Nimzo-Indian Defense"),
    "d4 Nf6 c4 e6 Nf3 b6" to ("E12" to "Queen's Indian Defense"),
    "d4 Nf6 c4 g6 Nc3 d5" to ("D80" to "Grünfeld Defense"),
    "d4 Nf6 c4 c5" to ("A56" to "Benoni Defense"),
    "d4 f5" to ("A80" to "Dutch Defense"),
    "c4 e5" to ("A20" to "English Opening: Reversed Sicilian"),
    "c4 c5" to ("A30" to "English Opening: Symmetrical"),
    "c4" to ("A10" to "English Opening"),
    "Nf3 d5" to ("A06" to "Réti Opening"),
    "f4" to ("A02" to "Bird's Opening"),
    "b3" to ("A01" to "Nimzo-Larsen Attack"),
    "e4" to ("B00" to "King's Pawn Opening"),
    "d4" to ("A40" to "Queen's Pawn Opening"),
  ).map { (line, pair) ->
    Entry(line.split(' '), pair.first, pair.second)
  }.sortedByDescending { it.tokens.size }

  fun recognize(sanMoves: List<String>): OpeningInfo? {
    if (sanMoves.isEmpty()) return null
    val cleanMoves = sanMoves.map { it.trimEnd('+', '#') }
    for (entry in entries) {
      if (cleanMoves.size >= entry.tokens.size) {
        var match = true
        for (i in entry.tokens.indices) {
          if (cleanMoves[i] != entry.tokens[i]) {
            match = false
            break
          }
        }
        if (match) {
          return OpeningInfo(
            eco = entry.eco,
            name = entry.name,
            movesLine = formatMovesLine(entry.tokens),
          )
        }
      }
    }
    return null
  }

  private fun formatMovesLine(tokens: List<String>): String {
    val sb = StringBuilder()
    var i = 0
    var moveNum = 1
    while (i < tokens.size) {
      if (i > 0) sb.append(' ')
      sb.append(moveNum).append('.').append(tokens[i])
      if (i + 1 < tokens.size) {
        sb.append(' ').append(tokens[i + 1])
      }
      i += 2
      moveNum++
    }
    return sb.toString()
  }
}
