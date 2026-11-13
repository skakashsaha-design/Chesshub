package com.example.bots

import com.example.chess.BotProfile
import com.example.chess.GameResult
import com.example.chess.Move
import com.example.chess.PieceCode
import com.example.chess.PieceType
import com.example.chess.TerminationReason

/**
 * Lightweight, offline contextual dialogue generator for ChessHub bots.
 * Keeps zero heavy allocations in memory for 1 GB RAM devices.
 */
object BotDialogueEngine {

  enum class Tone {
    PLAYFUL,
    TACTICAL,
    MASTER,
  }

  fun toneFor(bot: BotProfile): Tone = when {
    bot.rating < 800 -> Tone.PLAYFUL
    bot.rating < 1700 -> Tone.TACTICAL
    else -> Tone.MASTER
  }

  fun greetingFor(bot: BotProfile): String = when (bot.id) {
    "bot_spark" -> "Hi! I'm Spark (200 Elo). Let's take it easy and have fun!"
    "bot_pawnling" -> "Ready when you are! Pawns forward and let's learn together."
    "bot_nova" -> "Hey there! Expect a lively, open game—good luck!"
    "bot_rook" -> "Solid files and clean trades. Show me your opening!"
    "bot_bishop" -> "Careful along those long diagonals—let's play!"
    "bot_knight" -> "Watch out for knight forks! Your move."
    "bot_titan" -> "Every square matters. Let's test our positional grit."
    "bot_master" -> "Stay sharp. I'll punish even small inaccuracies."
    "bot_apex" -> "Maximum precision engaged. Play your very best chess."
    else -> "Custom ${bot.rating} Elo ready. Let's begin!"
  }

  fun onGameStart(bot: BotProfile, botPlaysWhite: Boolean): String {
    val sideNote = if (botPlaysWhite) "I'll open with White." else "You have the first move!"
    return when (toneFor(bot)) {
      Tone.PLAYFUL -> "${greetingFor(bot)} $sideNote"
      Tone.TACTICAL -> "Let's have a clean match. $sideNote"
      Tone.MASTER -> "${greetingFor(bot)} $sideNote"
    }
  }

  fun onMovePlayed(
    bot: BotProfile,
    move: Move,
    byBot: Boolean,
    isCheck: Boolean,
    moveNumber: Int,
  ): String? {
    val tone = toneFor(bot)
    if (isCheck) {
      return if (byBot) {
        when (tone) {
          Tone.PLAYFUL -> "Check! Watch your king!"
          Tone.TACTICAL -> "Check! Check your legal escapes or blocks."
          Tone.MASTER -> "Check. Find the cleanest defense."
        }
      } else {
        when (tone) {
          Tone.PLAYFUL -> "Whoa, nice check! Let me find a safe square."
          Tone.TACTICAL -> "Sharp check! Calculating my king's options."
          Tone.MASTER -> "Accurate check. I'll defend carefully."
        }
      }
    }

    if (move.promotion != null) {
      return if (byBot) {
        "Pawn promoted to ${move.promotion.name.lowercase()}!"
      } else {
        "Big promotion! Things just got serious."
      }
    }

    if (move.isCastleKingside || move.isCastleQueenside) {
      return if (byBot) {
        when (tone) {
          Tone.PLAYFUL -> "Tucking my king into a cozy corner!"
          Tone.TACTICAL -> "King castled safely—time to mobilize the rooks."
          Tone.MASTER -> "King safety secured. Let's fight for the center."
        }
      } else {
        when (tone) {
          Tone.PLAYFUL -> "Nice castle! Safe king, happy game."
          Tone.TACTICAL -> "Solid castling. Let's see where the pawns break."
          Tone.MASTER -> "Well timed castling. Now the middlegame begins."
        }
      }
    }

    val capturedType = PieceCode.typeOf(move.capturedPiece)
    if (capturedType == PieceType.QUEEN) {
      return if (byBot) {
        when (tone) {
          Tone.PLAYFUL -> "Caught the queen! Did you see that?"
          Tone.TACTICAL -> "The queens are off—endgame precision matters now."
          Tone.MASTER -> "Queen captured. Conversion is all about technique."
        }
      } else {
        when (tone) {
          Tone.PLAYFUL -> "Ouch, my queen! Well spotted."
          Tone.TACTICAL -> "Strong capture! Let's see how the endgame plays out."
          Tone.MASTER -> "Queen off the board. I'll tighten my structure."
        }
      }
    }

    if (capturedType == PieceType.ROOK || capturedType == PieceType.BISHOP || capturedType == PieceType.KNIGHT) {
      if (byBot && moveNumber % 2 == 0) {
        return when (tone) {
          Tone.PLAYFUL -> "Snagged a piece—keep fighting!"
          Tone.TACTICAL -> "Material gained. Look for counterplay!"
          Tone.MASTER -> "Clean capture. Maintain your coordination."
        }
      }
    }

    // Periodic flavor comment so dialogue stays calm and non-intrusive
    if (byBot && moveNumber in listOf(4, 10, 18, 28)) {
      return when (tone) {
        Tone.PLAYFUL -> "Move $moveNumber—this is getting fun!"
        Tone.TACTICAL -> "Entering a key phase on move $moveNumber."
        Tone.MASTER -> "Move $moveNumber. Watch the weak squares."
      }
    }
    return null
  }

  fun onDrawDeclined(bot: BotProfile): String = when (toneFor(bot)) {
    Tone.PLAYFUL -> "Let's keep playing a little longer!"
    Tone.TACTICAL -> "There's still plenty of play on the board—I'll pass on the draw."
    Tone.MASTER -> "Draw declined. The position still holds decisive chances."
  }

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
        else -> "Well fought! That ends in an honorable draw."
      }
    }

    return if (playerWon) {
      when (toneFor(bot)) {
        Tone.PLAYFUL -> "You got me! Awesome game—want a rematch?"
        Tone.TACTICAL -> "Well played! You out-calculated me today."
        Tone.MASTER -> "Impressive precision. You earned that victory!"
      }
    } else if (botWon) {
      when (toneFor(bot)) {
        Tone.PLAYFUL -> "Woohoo! Good game—ready to try again?"
        Tone.TACTICAL -> "Good match! Review the tactics and challenge me again."
        Tone.MASTER -> "Checkmate. Analyze the critical turning points and rematch anytime."
      }
    } else {
      "Good game!"
    }
  }
}
