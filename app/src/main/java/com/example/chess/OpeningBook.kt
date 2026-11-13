package com.example.chess

data class OpeningInfo(
  val eco: String,
  val name: String,
  val movesLine: String,
  val isGambit: Boolean = false,
  val category: String = "Openings",
  val planSummary: String = "",
)

data class OpeningContinuation(
  val nextSan: String,
  val eco: String,
  val openingName: String,
)

data class BookRepertoire(
  val id: String,
  val eco: String,
  val name: String,
  val category: String, // "Gambits", "1.e4", "1.d4", "Flank"
  val isGambit: Boolean,
  val practiceAs: PieceColor,
  val movesTokens: List<String>,
  val movesLine: String,
  val planSummary: String,
)

/**
 * Curated ECO opening & gambit book (< 12 KB in RAM).
 * Supports longest-prefix recognition, Gambit tagging, Opening Explorer continuations,
 * and All Book Move Practice repertoires.
 */
object OpeningBook {

  private data class Entry(
    val id: String,
    val tokens: List<String>,
    val eco: String,
    val name: String,
    val isGambit: Boolean,
    val category: String,
    val practiceAs: PieceColor,
    val planSummary: String,
  )

  private fun entry(
    id: String,
    line: String,
    eco: String,
    name: String,
    isGambit: Boolean = false,
    category: String = "1.e4",
    practiceAs: PieceColor = PieceColor.WHITE,
    planSummary: String = "Control central squares and develop minor pieces swiftly.",
  ) = Entry(id, line.split(' '), eco, name, isGambit, category, practiceAs, planSummary)

  private val entries: List<Entry> = listOf(
    // Gambits
    entry("danish_gambit", "e4 e5 d4 exd4 c3 dxc3 Bc4 cxb2 Bxb2", "C21", "Danish Gambit", true, "Gambits", PieceColor.WHITE, "Sacrifice two pawns for blistering bishop diagonals toward f7 and g7."),
    entry("evans_gambit", "e4 e5 Nf3 Nc6 Bc4 Bc5 b4 Bxb4 c3 Ba5 d4", "C51", "Evans Gambit", true, "Gambits", PieceColor.WHITE, "Deflect Black's bishop and seize a roaring d4-c3 pawn center."),
    entry("kings_gambit_acc", "e4 e5 f4 exf4 Nf3 g5", "C37", "King's Gambit Accepted", true, "Gambits", PieceColor.WHITE, "Challenge Black's center immediately and open the f-file for the rook."),
    entry("kings_gambit", "e4 e5 f4", "C30", "King's Gambit", true, "Gambits", PieceColor.WHITE, "Aggressive romantic-era gambit striking at e5 on move two."),
    entry("vienna_gambit", "e4 e5 Nc3 Nf6 f4", "C29", "Vienna Gambit", true, "Gambits", PieceColor.WHITE, "Undermine Black's center while keeping queenside development flexible."),
    entry("halloween_gambit", "e4 e5 Nf3 Nc6 Nc3 Nf6 Nxe5 Nxe5 d4", "C47", "Halloween Gambit", true, "Gambits", PieceColor.WHITE, "Sacrifice a knight to drive Black's pieces backward with a mobile pawn phalanx."),
    entry("smith_morra", "e4 c5 d4 cxd4 c3 dxc3 Nxc3", "B21", "Smith-Morra Gambit", true, "Gambits", PieceColor.WHITE, "Give up a central pawn vs the Sicilian for open c/d-files and rapid development."),
    entry("stafford_gambit", "e4 e5 Nf3 Nf6 Nxe5 Nc6 Nxc6 dxc6", "C42", "Stafford Gambit", true, "Gambits", PieceColor.BLACK, "Play for rapid bishop/knight attacks on f2 and kingside traps."),
    entry("englund_gambit", "d4 e5 dxe5 Nc6 Nf3 Qe7", "A40", "Englund Gambit", true, "Gambits", PieceColor.BLACK, "Sharp offbeat counter-gambit against 1.d4 aiming for quick queenside castling."),
    entry("budapest_gambit", "d4 Nf6 c4 e5 dxe5 Ng4", "A52", "Budapest Gambit", true, "Gambits", PieceColor.BLACK, "Regain the e5 pawn with active minor-piece tactics against White's kingside."),
    entry("benko_gambit", "d4 Nf6 c4 c5 d5 b5 cxb5 a6", "A57", "Benko Gambit", true, "Gambits", PieceColor.BLACK, "Trade a flank pawn for permanent pressure down the open a- and b-files."),
    entry("albin_counter", "d4 d5 c4 e5 dxe5 d4", "D08", "Albin Countergambit", true, "Gambits", PieceColor.BLACK, "Cramp White's kingside knight with an advanced d4 wedge."),
    entry("blackmar_diemer", "d4 d5 e4 dxe4 Nc3 Nf6 f3", "D00", "Blackmar-Diemer Gambit", true, "Gambits", PieceColor.WHITE, "Sacrifice a pawn for an open f-file and early kingside attack."),
    entry("fried_liver", "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Nxd5 Nxf7", "C57", "Fried Liver Attack", true, "Gambits", PieceColor.WHITE, "Sacrifice a knight on f7 to drag Black's king into the crossfire."),

    // 1.e4 Openings
    entry("ruy_morphy_closed", "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O", "C78", "Ruy Lopez: Closed Morphy", false, "1.e4", PieceColor.WHITE, "Prepare c3 and d4 while maintaining long-term Spanish pressure."),
    entry("ruy_morphy", "e4 e5 Nf3 Nc6 Bb5 a6", "C70", "Ruy Lopez: Morphy Defense", false, "1.e4", PieceColor.BLACK, "Question the Spanish bishop immediately before committing knights."),
    entry("ruy_berlin", "e4 e5 Nf3 Nc6 Bb5 Nf6", "C65", "Ruy Lopez: Berlin Defense", false, "1.e4", PieceColor.BLACK, "Solid counter-attack on e4 leading to dynamic endgame structures."),
    entry("ruy_lopez", "e4 e5 Nf3 Nc6 Bb5", "C60", "Ruy Lopez", false, "1.e4", PieceColor.WHITE, "Put classical pressure on the defender of Black's e5 pawn."),
    entry("giuoco_piano", "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3", "C50", "Italian Game: Giuoco Piano", false, "1.e4", PieceColor.WHITE, "Build a harmonious center and maneuver knights toward the kingside."),
    entry("two_knights", "e4 e5 Nf3 Nc6 Bc4 Nf6", "C55", "Italian Game: Two Knights Defense", false, "1.e4", PieceColor.BLACK, "Invite sharp center counterplay rather than passive symmetry."),
    entry("italian_game", "e4 e5 Nf3 Nc6 Bc4", "C50", "Italian Game", false, "1.e4", PieceColor.WHITE, "Develop the bishop onto the active a2-g8 diagonal targeting f7."),
    entry("scotch_game", "e4 e5 Nf3 Nc6 d4 exd4 Nxd4", "C45", "Scotch Game", false, "1.e4", PieceColor.WHITE, "Open the center early and seize space with active piece coordination."),
    entry("petrov_defense", "e4 e5 Nf3 Nf6", "C42", "Petrov's Defense", false, "1.e4", PieceColor.BLACK, "Symmetrical, rock-solid counter-attack against e4."),
    entry("vienna_game", "e4 e5 Nc3", "C25", "Vienna Game", false, "1.e4", PieceColor.WHITE, "Reinforce e4 and keep the f4 break in reserve."),
    entry("bishops_opening", "e4 e5 Bc4", "C23", "Bishop's Opening", false, "1.e4", PieceColor.WHITE, "Target f7 immediately without blocking the f-pawn."),
    entry("sicilian_najdorf", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6", "B90", "Sicilian Defense: Najdorf", false, "1.e4", PieceColor.BLACK, "Keep e5/b5 flexible while fighting dynamically on the dark squares."),
    entry("sicilian_accel_dragon", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 g6", "B34", "Sicilian Defense: Accelerated Dragon", false, "1.e4", PieceColor.BLACK, "Fianchetto the dark-squared bishop without wasting time on d6."),
    entry("sicilian_dragon", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6", "B70", "Sicilian Defense: Dragon", false, "1.e4", PieceColor.BLACK, "Pair a monstrous g7 bishop with queenside counterplay on the c-file."),
    entry("sicilian_french_var", "e4 c5 Nf3 e6", "B40", "Sicilian Defense: French Variation", false, "1.e4", PieceColor.BLACK, "Flexible Sicilian setup preparing ...d5 or ...a6/...Qc7."),
    entry("sicilian_alapin", "e4 c5 c3", "B22", "Sicilian Defense: Alapin", false, "1.e4", PieceColor.WHITE, "Prepare 2.d4 to establish a broad central pawn duo."),
    entry("sicilian", "e4 c5", "B20", "Sicilian Defense", false, "1.e4", PieceColor.BLACK, "Fight for an asymmetrical game from move one."),
    entry("french_classical", "e4 e6 d4 d5 Nc3 Nf6", "C11", "French Defense: Classical", false, "1.e4", PieceColor.BLACK, "Challenge White's center with ...c5 and ...Nc6 counterpressure."),
    entry("french_advance", "e4 e6 d4 d5 e5 c5", "C02", "French Defense: Advance", false, "1.e4", PieceColor.BLACK, "Attack the base of White's d4-e5 pawn chain with ...c5 and ...Qb6."),
    entry("french", "e4 e6", "C00", "French Defense", false, "1.e4", PieceColor.BLACK, "Prepare ...d5 to contest the center safely."),
    entry("caro_kann_advance", "e4 c6 d4 d5 e5 Bf5", "B12", "Caro-Kann: Advance Variation", false, "1.e4", PieceColor.BLACK, "Develop the light-squared bishop outside the pawn chain before ...e6."),
    entry("caro_kann_classical", "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5", "B18", "Caro-Kann: Classical", false, "1.e4", PieceColor.BLACK, "Maintain rock-solid structure with clean minor-piece trades."),
    entry("caro_kann", "e4 c6", "B10", "Caro-Kann Defense", false, "1.e4", PieceColor.BLACK, "Solid positional defense aiming for ...d5."),
    entry("scandinavian", "e4 d5 exd5 Qxd5 Nc3 Qa5", "B01", "Scandinavian Defense", false, "1.e4", PieceColor.BLACK, "Immediately liquidate e4 and develop the light-squared bishop cleanly."),
    entry("scandinavian_base", "e4 d5", "B01", "Scandinavian Defense", false, "1.e4", PieceColor.BLACK, "Immediate central confrontation."),
    entry("pirc", "e4 d6 d4 Nf6 Nc3 g6", "B07", "Pirc Defense", false, "1.e4", PieceColor.BLACK, "Hypermodern defense inviting White forward before striking back."),
    entry("alekhine", "e4 Nf6 e5 Nd5", "B02", "Alekhine's Defense", false, "1.e4", PieceColor.BLACK, "Provoke White's pawns forward and undermine them later."),
    entry("modern", "e4 g6", "B06", "Modern Defense", false, "1.e4", PieceColor.BLACK, "Flexible fianchetto system targeting the center from afar."),

    // 1.d4 Openings
    entry("queens_gambit_declined", "d4 d5 c4 e6 Nc3 Nf6 Bg5", "D50", "Queen's Gambit Declined", false, "1.d4", PieceColor.BLACK, "Hold the d5 stronghold firmly and complete kingside castling."),
    entry("slav_defense", "d4 d5 c4 c6 Nf3 Nf6 Nc3", "D15", "Slav Defense", false, "1.d4", PieceColor.BLACK, "Support d5 without locking in the c8 bishop."),
    entry("queens_gambit_acc", "d4 d5 c4 dxc4 Nf3 Nf6 e3", "D20", "Queen's Gambit Accepted", true, "Gambits", PieceColor.WHITE, "Recover the c4 pawn with active bishop play and center control."),
    entry("queens_gambit", "d4 d5 c4", "D06", "Queen's Gambit", true, "Gambits", PieceColor.WHITE, "Offer a wing pawn to deflect Black's d5 anchor."),
    entry("london_system", "d4 d5 Bf4 Nf6 e3 e6 Nf3", "D00", "London System", false, "1.d4", PieceColor.WHITE, "Construct a solid pyramid center with reliable e5 outpost control."),
    entry("kings_indian", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6", "E60", "King's Indian Defense", false, "1.d4", PieceColor.BLACK, "Counter-attack with ...e5 and a fierce kingside pawn storm."),
    entry("nimzo_indian", "d4 Nf6 c4 e6 Nc3 Bb4", "E20", "Nimzo-Indian Defense", false, "1.d4", PieceColor.BLACK, "Pin c3 immediately and fight for the e4 square."),
    entry("queens_indian", "d4 Nf6 c4 e6 Nf3 b6", "E12", "Queen's Indian Defense", false, "1.d4", PieceColor.BLACK, "Control e4 from the b7 fianchetto."),
    entry("grunfeld", "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5", "D85", "Grünfeld Defense", false, "1.d4", PieceColor.BLACK, "Let White build a big center, then hammer it with ...c5 and ...Bg7."),
    entry("benoni", "d4 Nf6 c4 c5 d5 e6", "A60", "Benoni Defense", false, "1.d4", PieceColor.BLACK, "Imbalanced pawn structure favoring a queenside majority push."),
    entry("dutch", "d4 f5 g3 Nf6 Bg2 e6", "A80", "Dutch Defense", false, "1.d4", PieceColor.BLACK, "Clamp down on e4 and build a kingside attack."),

    // Flank & Base Openings
    entry("english_sicilian", "c4 e5 Nc3 Nf6", "A20", "English Opening: Reversed Sicilian", false, "Flank", PieceColor.WHITE, "Play a Sicilian Defense with an extra tempo."),
    entry("english_sym", "c4 c5 Nc3 Nc6", "A30", "English Opening: Symmetrical", false, "Flank", PieceColor.WHITE, "Control d5 with bishops on g2 and knight maneuvers."),
    entry("english", "c4", "A10", "English Opening", false, "Flank", PieceColor.WHITE, "Control d5 from the c-file before committing central pawns."),
    entry("reti", "Nf3 d5 c4", "A09", "Réti Opening", false, "Flank", PieceColor.WHITE, "Hypermodern flank pressure against Black's d5 pawn."),
    entry("birds", "f4 d5 Nf3", "A03", "Bird's Opening", false, "Flank", PieceColor.WHITE, "Seize e5 control with kingside attacking prospects."),
    entry("nimzo_larsen", "b3 e5 Bb2", "A01", "Nimzo-Larsen Attack", false, "Flank", PieceColor.WHITE, "Fianchetto the dark-squared bishop to pressure the center."),
    entry("e4_base", "e4", "B00", "King's Pawn Opening", false, "1.e4", PieceColor.WHITE, "Open lines for the bishop and queen from move one."),
    entry("d4_base", "d4", "A40", "Queen's Pawn Opening", false, "1.d4", PieceColor.WHITE, "Claim central space backed directly by the queen."),
  ).sortedByDescending { it.tokens.size }

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
            isGambit = entry.isGambit,
            category = entry.category,
            planSummary = entry.planSummary,
          )
        }
      }
    }
    return null
  }

  fun allRepertoires(): List<BookRepertoire> =
    entries
      .filter { it.tokens.size >= 2 }
      .sortedWith(compareBy<Entry>({ !it.isGambit }, { it.category }, { it.name }))
      .map { e ->
        BookRepertoire(
          id = e.id,
          eco = e.eco,
          name = e.name,
          category = e.category,
          isGambit = e.isGambit,
          practiceAs = e.practiceAs,
          movesTokens = e.tokens,
          movesLine = formatMovesLine(e.tokens),
          planSummary = e.planSummary,
        )
      }

  fun allGambits(): List<BookRepertoire> = allRepertoires().filter { it.isGambit }

  fun formatMovesLine(tokens: List<String>): String {
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

  fun exploreContinuations(sanMoves: List<String>): List<OpeningContinuation> {
    val cleanMoves = sanMoves.map { it.trimEnd('+', '#') }
    val seenMoves = HashSet<String>()
    val out = ArrayList<OpeningContinuation>()
    for (entry in entries) {
      if (entry.tokens.size > cleanMoves.size) {
        var prefixMatches = true
        for (i in cleanMoves.indices) {
          if (entry.tokens[i] != cleanMoves[i]) {
            prefixMatches = false
            break
          }
        }
        if (prefixMatches) {
          val nextToken = entry.tokens[cleanMoves.size]
          if (seenMoves.add(nextToken)) {
            out.add(
              OpeningContinuation(
                nextSan = nextToken,
                eco = entry.eco,
                openingName = entry.name,
              )
            )
          }
        }
      }
    }
    return out
  }

  fun isKnownBookPrefix(sanMoves: List<String>): Boolean {
    if (sanMoves.isEmpty()) return false
    val cleanMoves = sanMoves.map { it.trimEnd('+', '#') }
    return entries.any { entry ->
      entry.tokens.size >= cleanMoves.size &&
        cleanMoves.indices.all { i -> entry.tokens[i] == cleanMoves[i] }
    }
  }
}
