package com.example.chess

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedPgn(
  val tags: Map<String, String>,
  val moves: List<RecordedMove>,
  val finalState: BoardState,
  val result: GameResult,
  val opening: OpeningInfo?,
)

object PgnCodec {

  fun export(
    whiteName: String,
    blackName: String,
    result: GameResult,
    dateMillis: Long,
    timeControl: String,
    eco: String?,
    openingName: String?,
    moves: List<RecordedMove>,
    eventName: String = "ChessHub Offline Game",
  ): String {
    val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(dateMillis))
    val sb = StringBuilder(512)
    sb.append("[Event \"").append(escapeTag(eventName)).append("\"]\n")
    sb.append("[Site \"ChessHub Android\"]\n")
    sb.append("[Date \"").append(dateStr).append("\"]\n")
    sb.append("[White \"").append(escapeTag(whiteName)).append("\"]\n")
    sb.append("[Black \"").append(escapeTag(blackName)).append("\"]\n")
    sb.append("[Result \"").append(result.pgnValue).append("\"]\n")
    if (timeControl.isNotBlank()) {
      sb.append("[TimeControl \"").append(escapeTag(timeControl)).append("\"]\n")
    }
    if (!eco.isNullOrBlank()) {
      sb.append("[ECO \"").append(escapeTag(eco)).append("\"]\n")
    }
    if (!openingName.isNullOrBlank()) {
      sb.append("[Opening \"").append(escapeTag(openingName)).append("\"]\n")
    }
    sb.append('\n')

    var idx = 0
    var moveNum = 1
    while (idx < moves.size) {
      if (idx > 0) sb.append(' ')
      sb.append(moveNum).append(". ").append(moves[idx].san)
      if (idx + 1 < moves.size) {
        sb.append(' ').append(moves[idx + 1].san)
      }
      idx += 2
      moveNum++
    }
    if (moves.isNotEmpty()) sb.append(' ')
    sb.append(result.pgnValue)
    return sb.toString()
  }

  fun parse(pgnText: String): Result<ParsedPgn> {
    val trimmed = pgnText.trim()
    if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Empty PGN"))

    val tags = LinkedHashMap<String, String>()
    val bodyLines = ArrayList<String>()

    val tagRegex = Regex("""^\[\s*([A-Za-z0-9_]+)\s+"((?:[^"\\]|\\.)*)"\s*\]\s*$""")
    for (rawLine in trimmed.lines()) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      val match = tagRegex.matchEntire(line)
      if (match != null) {
        tags[match.groupValues[1]] = match.groupValues[2].replace("\\\"", "\"")
      } else {
        bodyLines.add(line)
      }
    }

    val startFen = tags["FEN"]
    var state = if (!startFen.isNullOrBlank()) {
      ChessNotation.fromFen(startFen)
        ?: return Result.failure(IllegalArgumentException("Invalid FEN tag"))
    } else {
      ChessRules.initialBoardState()
    }

    val cleanedMovetext = stripCommentsAndVariations(bodyLines.joinToString(" "))
    val tokens = cleanedMovetext.split(Regex("\\s+")).filter { it.isNotBlank() }

    val recordedMoves = ArrayList<RecordedMove>()
    var parsedResult = when (tags["Result"]) {
      "1-0" -> GameResult.WHITE_WIN
      "0-1" -> GameResult.BLACK_WIN
      "1/2-1/2" -> GameResult.DRAW
      else -> GameResult.IN_PROGRESS
    }

    for (rawToken in tokens) {
      val token = rawToken.trim()
      if (token.isEmpty()) continue
      when (token) {
        "1-0" -> {
          parsedResult = GameResult.WHITE_WIN
          break
        }
        "0-1" -> {
          parsedResult = GameResult.BLACK_WIN
          break
        }
        "1/2-1/2" -> {
          parsedResult = GameResult.DRAW
          break
        }
        "*" -> break
      }
      // Skip move numbers like "1.", "12...", or NAGs like "$1"
      if (token.startsWith("$") || token.matches(Regex("""\d+\.+"""))) continue
      // Handle attached move number like "1.e4" or "1...e5"
      val sanToken = token.replace(Regex("""^\d+\.+"""), "")
      if (sanToken.isEmpty()) continue

      val move = ChessNotation.parseSanMove(state, sanToken)
        ?: return Result.failure(IllegalArgumentException("Illegal or unrecognized move: $sanToken"))
      val canonicalSan = ChessNotation.toSan(state, move)
      state = ChessRules.applyMove(state, move)
      recordedMoves.add(
        RecordedMove(
          move = move,
          san = canonicalSan,
          uci = move.toUci(),
          fenAfter = ChessNotation.toFen(state),
        )
      )
    }

    if (recordedMoves.isEmpty() && tags.isEmpty()) {
      return Result.failure(IllegalArgumentException("No valid moves found in PGN"))
    }

    val opening = OpeningBook.recognize(recordedMoves.map { it.san })
    return Result.success(
      ParsedPgn(
        tags = tags,
        moves = recordedMoves,
        finalState = state,
        result = parsedResult,
        opening = opening,
      )
    )
  }

  private fun stripCommentsAndVariations(input: String): String {
    val sb = StringBuilder(input.length)
    var braceDepth = 0
    var parenDepth = 0
    for (ch in input) {
      when (ch) {
        '{' -> braceDepth++
        '}' -> if (braceDepth > 0) braceDepth--
        '(' -> if (braceDepth == 0) parenDepth++
        ')' -> if (braceDepth == 0 && parenDepth > 0) parenDepth--
        else -> if (braceDepth == 0 && parenDepth == 0) sb.append(ch)
      }
    }
    return sb.toString()
  }

  private fun escapeTag(v: String): String = v.replace("\"", "\\\"")
}
