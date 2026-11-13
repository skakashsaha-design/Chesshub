package com.example.bots

import com.example.chess.BotProfile
import com.example.chess.GameResult
import com.example.chess.Move
import com.example.chess.OpeningInfo
import com.example.chess.PieceCode
import com.example.chess.PieceType
import com.example.chess.TerminationReason

/**
 * Unique, character-authentic offline dialogue engine for every bot in ChessHub
 * plus live Opening / Gambit recognition callouts.
 */
object BotDialogueEngine {

  private data class Voice(
    val greeting: String,
    val checkByBot: String,
    val checkByPlayer: String,
    val gambitReaction: String,
    val captureByBot: String,
    val castle: String,
    val drawDeclined: String,
    val playerWon: String,
    val botWon: String,
    val midgame4: String,
    val midgame12: String,
  )

  private val voices: Map<String, Voice> = mapOf(
    "bot_spark" to Voice(
      greeting = "Hi! I'm Spark (200 Elo). Let's keep the board joyful and learn together!",
      checkByBot = "Spark: Boop—Check! Oops, did you see that?",
      checkByPlayer = "Whoa, nice check! Let me scoot my king to safety.",
      gambitReaction = "Ooh, a daring gambit! I love free pawns... or is it a trap?!",
      captureByBot = "Yoink! I grabbed a piece—still lots of game left!",
      castle = "Tucking my king into a cozy little castle!",
      drawDeclined = "Aww, let's keep playing a few more fun moves!",
      playerWon = "You beat me! High five—want another friendly round?",
      botWon = "Wait, I won?! What a wild game—rematch anytime!",
      midgame4 = "Move 4 already! This position looks exciting.",
      midgame12 = "Move 12—let's see what happens next!",
    ),
    "bot_pawnling" to Voice(
      greeting = "Pawnling reporting! Pawns forward—no retreating today!",
      checkByBot = "Check! Watch the swarm coming down the board!",
      checkByPlayer = "Hey! Sneaky check—good eyes!",
      gambitReaction = "A gambit?! Now you're speaking my language!",
      captureByBot = "Got one! My pawns are cheering!",
      castle = "Okay, king tucked—time to launch the pawn storm!",
      drawDeclined = "No draws while I still have pawns ready to promote!",
      playerWon = "You broke through my pawn line! Well played!",
      botWon = "Charge paid off! Ready for round two?",
      midgame4 = "Full steam ahead on move 4!",
      midgame12 = "Midgame brawl—every pawn push counts!",
    ),
    "bot_nova" to Voice(
      greeting = "Nova online! Expect open lines, checks, and sparkling surprises.",
      checkByBot = "Zap! Check—check your escape squares!",
      checkByPlayer = "Bright idea with that check! Calculating...",
      gambitReaction = "Sharp gambit! Fireworks on the board!",
      captureByBot = "Swiped clean! Time to turn up the tempo.",
      castle = "Castle complete—rooks charging the center.",
      drawDeclined = "Still way too many tactics on the board for a draw!",
      playerWon = "Out-sparked me! Clean combination.",
      botWon = "Nova strikes! Analyze that checkmate and rematch!",
      midgame4 = "Opening phase heating up cleanly.",
      midgame12 = "Move 12—tactical radar is buzzing!",
    ),
    "bot_rook" to Voice(
      greeting = "Rook here. Respect open files, castle early, and trade clean.",
      checkByBot = "Check along the line. Defend carefully.",
      checkByPlayer = "A well-structured check. Finding the solid reply.",
      gambitReaction = "A speculative gambit—I'll consolidate and test its soundness.",
      captureByBot = "Solid capture. Simplifying toward a clean advantage.",
      castle = "King safe, rooks connected. Classic discipline.",
      drawDeclined = "The structure isn't settled yet—let's play on.",
      playerWon = "You cracked my fortress—impressive technique!",
      botWon = "Structure held firm. Want to study the critical trades?",
      midgame4 = "Development first, tactics second.",
      midgame12 = "Rook files are starting to open up.",
    ),
    "bot_bishop" to Voice(
      greeting = "Bishop at your service. Mind those long diagonals!",
      checkByBot = "Diagonal Check! Look across the whole board.",
      checkByPlayer = "Clever diagonal check—covering the line.",
      gambitReaction = "An audacious gambit! Open diagonals favor the bold.",
      captureByBot = "Sliced that piece right off the board!",
      castle = "King tucked neatly; bishops eyeing your camp.",
      drawDeclined = "There is asymmetric bishop play left—I decline the draw.",
      playerWon = "You locked down my bishops brilliantly—well earned win!",
      botWon = "The bishop pair strikes again! Care for a rematch?",
      midgame4 = "Targeting the light and dark weak squares.",
      midgame12 = "Check those bishop pins on move 12!",
    ),
    "bot_knight" to Voice(
      greeting = "Knight here! Watch out for sneaky L-shaped forks.",
      checkByBot = "Check! Check every piece your king might leave undefended!",
      checkByPlayer = "Bold check! Let's see if you overlooked a counter-jump.",
      gambitReaction = "Oh, a spicy gambit! Knights thrive in messy positions!",
      captureByBot = "Gotcha! That square belonged to me.",
      castle = "King tucked—now watch where the knights hop.",
      drawDeclined = "With knights still hopping, anything can happen—no draw yet!",
      playerWon = "Great outpost control—you caged my knights cleanly!",
      botWon = "Tactical fork lands! Ready to run it back?",
      midgame4 = "Steeds saddled for central outposts.",
      midgame12 = "Complications building—stay sharp!",
    ),
    "bot_titan" to Voice(
      greeting = "Titan initialized (1850). Every tempo and pawn weakness matters.",
      checkByBot = "Forcing check. Find the single accurate response.",
      checkByPlayer = "Strong check. Tightening defensive coordination.",
      gambitReaction = "Challenging gambit choice. I'll fight for central compensation.",
      captureByBot = "Material secured without conceding the initiative.",
      castle = "Safety first; now the positional squeeze begins.",
      drawDeclined = "I hold clear structural trumps—draw declined.",
      playerWon = "Impressive positional mastery. You broke through!",
      botWon = "Slow pressure pays dividends. Review your pawn breaks!",
      midgame4 = "Claiming key central squares.",
      midgame12 = "Every weak square tells a story at move 12.",
    ),
    "bot_master" to Voice(
      greeting = "Master (2150) ready. Stay sharp—endgame precision starts in the opening.",
      checkByBot = "Check. Don't compromise your pawn structure.",
      checkByPlayer = "Accurate check. Calculating the cleanest simplification.",
      gambitReaction = "Classical gambit tension! Theory will decide who has compensation.",
      captureByBot = "Clean exchange—moving closer to a winning endgame.",
      castle = "King safeguarded. Time to coordinate heavy pieces.",
      drawDeclined = "The endgame nuances still offer winning chances.",
      playerWon = "Masterful play—you converted with surgical accuracy!",
      botWon = "Endgame discipline decided it. Let's analyze the turning point!",
      midgame4 = "Precision in transition matters most.",
      midgame12 = "Checking king opposition and minor-piece balances.",
    ),
    "bot_grandmaster" to Voice(
      greeting = "Grandmaster (2450) online. Prophylaxis and precision will be tested.",
      checkByBot = "Check. Notice how your dark squares weakened.",
      checkByPlayer = "Well-calculated check. Adjusting the defensive shell.",
      gambitReaction = "Elite-level gambit fight—prove your dynamic compensation!",
      captureByBot = "Material reclaimed; maintaining total board harmony.",
      castle = "Textbook king safety. Let's battle for the d5/e5 outposts.",
      drawDeclined = "Unbalanced Grandmaster struggle—keep fighting!",
      playerWon = "Grandmaster performance! Almost engine-perfect accuracy.",
      botWon = "Prophylaxis prevailed. Check the Game Review graphs!",
      midgame4 = "Controlling the opponent's counterplay before attacking.",
      midgame12 = "Deep middlegame maneuvering underway.",
    ),
    "bot_apex" to Voice(
      greeting = "Apex Max (2850) engaged. Every loose square carries a tactical tax.",
      checkByBot = "Forcing check—only one defense holds.",
      checkByPlayer = "Sharp check. Re-evaluating deep tactical lines.",
      gambitReaction = "High-voltage gambit! Calculation speed engaged.",
      captureByBot = "Punished cleanly. Watch the evaluation swing.",
      castle = "Castled and ready for full-board pressure.",
      drawDeclined = "Evaluation shows decisive imbalance—draw declined.",
      playerWon = "Astonishing calculation! Defeating 2850 takes brilliance!",
      botWon = "Tactical precision clinched it. Inspect the critical moments!",
      midgame4 = "Scanning multi-ply tactical combinations.",
      midgame12 = "Entering deep calculation territory.",
    ),
    "bot_omega" to Voice(
      greeting = "Omega 3200 online. Maximum depth active—show me your strongest line.",
      checkByBot = "Check. All variations converge rapidly.",
      checkByPlayer = "Resourceful check. Verifying deep refutation lines.",
      gambitReaction = "Bold gambit against 3200 Elo—let's test the concrete variations!",
      captureByBot = "Concrete calculation wins material cleanly.",
      castle = "King optimized. Full-board coordination unlocked.",
      drawDeclined = "Peak calculation indicates decisive winning play remains.",
      playerWon = "Incredible! Outplaying 3200 Elo is pure Grandmaster genius!",
      botWon = "Peak depth conversion. Use Game Review to study the PV line!",
      midgame4 = "Deep opening theory benchmarked.",
      midgame12 = "Zero guesswork—calculating six plies ahead.",
    ),
  )

  private fun voiceFor(bot: BotProfile): Voice = voices[bot.id] ?: Voice(
    greeting = "Custom ${bot.rating} Elo (${bot.playingStyle.label}) ready. Let's begin!",
    checkByBot = "${bot.name}: Check!",
    checkByPlayer = "${bot.name}: Sharp check—defending carefully.",
    gambitReaction = "Interesting gambit! Let's see how the compensation plays out.",
    captureByBot = "${bot.name}: Clean capture (${bot.playingStyle.label} style).",
    castle = "King castled safely.",
    drawDeclined = "Too much fight left on the board—draw declined.",
    playerWon = "Well played! You defeated ${bot.name} (${bot.rating} Elo)!",
    botWon = "Match concluded. Check Game Review to inspect every move!",
    midgame4 = "Move 4—finding the plan.",
    midgame12 = "Move 12—middlegame battle intensifies.",
  )

  fun greetingFor(bot: BotProfile): String = voiceFor(bot).greeting

  fun onGameStart(bot: BotProfile, botPlaysWhite: Boolean): String {
    val sideNote = if (botPlaysWhite) "I'll open with White." else "You have the first move!"
    return "${voiceFor(bot).greeting} $sideNote"
  }

  /**
   * Emits a clean live dialogue callout whenever a named Gambit or Book Opening is played in-game.
   */
  fun onOpeningDetected(bot: BotProfile?, opening: OpeningInfo): String {
    return if (opening.isGambit) {
      val react = bot?.let { " — ${voiceFor(it).gambitReaction}" } ?: " — sharp tactical play ahead!"
      "Gambit in play: ${opening.name} (${opening.eco})$react"
    } else {
      "Book Opening: ${opening.name} (${opening.eco})"
    }
  }

  fun onMovePlayed(
    bot: BotProfile,
    move: Move,
    byBot: Boolean,
    isCheck: Boolean,
    moveNumber: Int,
  ): String? {
    val v = voiceFor(bot)
    if (isCheck) return if (byBot) v.checkByBot else v.checkByPlayer
    if (move.promotion != null) {
      return if (byBot) "${bot.name} promoted a pawn to ${move.promotion.name.lowercase()}!"
      else "Huge promotion! The board just exploded!"
    }
    if (move.isCastleKingside || move.isCastleQueenside) {
      return if (byBot) v.castle else "Clean castling! Safety secured."
    }
    val capturedType = PieceCode.typeOf(move.capturedPiece)
    if (capturedType == PieceType.QUEEN) {
      return if (byBot) "${bot.name} won the queen! ${v.captureByBot}" else "Ouch, my queen! Well spotted."
    }
    if (capturedType == PieceType.ROOK || capturedType == PieceType.BISHOP || capturedType == PieceType.KNIGHT) {
      if (byBot && moveNumber % 2 == 0) return v.captureByBot
    }
    if (byBot && moveNumber == 4) return v.midgame4
    if (byBot && moveNumber == 12) return v.midgame12
    return null
  }

  fun onDrawDeclined(bot: BotProfile): String = voiceFor(bot).drawDeclined

  fun onGameFinished(
    bot: BotProfile,
    result: GameResult,
    termination: TerminationReason,
    botPlayedWhite: Boolean,
  ): String {
    val botWon = (result == GameResult.WHITE_WIN && botPlayedWhite) ||
      (result == GameResult.BLACK_WIN && !botPlayedWhite)
    val playerWon = (result == GameResult.WHITE_WIN && !botPlayedWhite) ||
      (result == GameResult.BLACK_WIN && botPlayedWhite)

    if (result == GameResult.DRAW) {
      return when (termination) {
        TerminationReason.STALEMATE -> "Stalemate! Not a single legal square left—fair draw."
        TerminationReason.THREEFOLD_REPETITION -> "Threefold repetition—a balanced draw!"
        else -> "Well fought! Honorable draw."
      }
    }
    val v = voiceFor(bot)
    return when {
      playerWon -> v.playerWon
      botWon -> v.botWon
      else -> "Good game!"
    }
  }
}
