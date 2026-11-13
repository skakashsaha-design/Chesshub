package com.example.chess

data class ChessRank(
  val title: String,
  val badge: String,
  val minElo: Int,
  val nextThreshold: Int?,
  val colorHex: Long,
)

object RankSystem {

  private val tiers = listOf(
    ChessRank("Novice", "NOV", 0, 400, 0xFF64748B),
    ChessRank("Beginner", "BEG", 400, 700, 0xFF38BDF8),
    ChessRank("Casual", "CAS", 700, 1000, 0xFF34D399),
    ChessRank("Club Player", "CLB", 1000, 1300, 0xFFFBBF24),
    ChessRank("Tactician", "TAC", 1300, 1600, 0xFFA78BFA),
    ChessRank("Candidate", "CM", 1600, 1900, 0xFFF97316),
    ChessRank("Master", "NM", 1900, 2200, 0xFFF43F5E),
    ChessRank("Grandmaster", "GM", 2200, 2600, 0xFFEAB308),
    ChessRank("Apex Max", "MAX", 2600, 3000, 0xFFEF4444),
    ChessRank("Omega 3200", "OMG", 3000, null, 0xFFDC2626),
  )

  fun rankForElo(elo: Int): ChessRank =
    tiers.lastOrNull { elo >= it.minElo } ?: tiers.first()

  fun progressToNextRank(elo: Int): Float {
    val rank = rankForElo(elo)
    val next = rank.nextThreshold ?: return 1f
    val span = (next - rank.minElo).coerceAtLeast(1)
    return ((elo - rank.minElo).toFloat() / span).coerceIn(0f, 1f)
  }
}
