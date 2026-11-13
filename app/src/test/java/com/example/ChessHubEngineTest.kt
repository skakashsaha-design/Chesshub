package com.example

import com.example.bots.BotCatalog
import com.example.bots.BotDialogueEngine
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.GameResult
import com.example.chess.Move
import com.example.chess.OpeningBook
import com.example.chess.PgnCodec
import com.example.chess.PieceColor
import com.example.chess.RankSystem
import com.example.chess.SquareUtil
import com.example.chess.TerminationReason
import com.example.engine.ChessEngine
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.UciLineParser
import com.example.puzzles.PuzzleCatalog
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.PieceStyleOption
import com.example.ui.board.gridOffsetToTopLeft
import com.example.ui.board.legalTargetMovesForSquare
import com.example.ui.board.resolveCastleRookPath
import com.example.ui.board.squareGridOffset
import com.example.ui.theme.AppAccentOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessHubEngineTest {

  @Test
  fun initialPositionHas20LegalMoves() {
    val state = ChessRules.initialBoardState()
    assertEquals(20, ChessRules.legalMoves(state).size)
  }

  @Test
  fun foolsMateIsCheckmate() {
    val pgn = "1. f3 e5 2. g4 Qh4#"
    val parsed = PgnCodec.parse(pgn).getOrThrow()
    val status = ChessRules.evaluateTerminalStatus(parsed.finalState, emptyMap())
    assertEquals(GameResult.BLACK_WIN, status.first)
    assertEquals(TerminationReason.CHECKMATE, status.second)
  }

  @Test
  fun stalemateIsDetected() {
    val fen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
    val state = ChessNotation.fromFen(fen)!!
    val status = ChessRules.evaluateTerminalStatus(state, emptyMap())
    assertEquals(GameResult.DRAW, status.first)
    assertEquals(TerminationReason.STALEMATE, status.second)
  }

  @Test
  fun enPassantAndCastlingWorkLegally() {
    val fen = "r3k2r/ppp1pppp/8/3pP3/8/8/PPPP1PPP/R3K2R w KQkq d6 0 2"
    val state = ChessNotation.fromFen(fen)!!
    val legalUcis = ChessRules.legalMoves(state).map { it.toUci() }
    assertTrue("Expected en passant e5d6", "e5d6" in legalUcis)
    assertTrue("Expected kingside castle e1g1", "e1g1" in legalUcis)
    assertTrue("Expected queenside castle e1c1", "e1c1" in legalUcis)
  }

  @Test
  fun insufficientMaterialIsDetected() {
    val fen = "8/8/4k3/8/8/3B4/4K3/8 w - - 0 1"
    val state = ChessNotation.fromFen(fen)!!
    assertTrue(ChessRules.hasInsufficientMaterial(state.board))
  }

  @Test
  fun openingRecognitionFindsRuyLopez() {
    val info = OpeningBook.recognize(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"))
    assertNotNull(info)
    assertEquals("C70", info?.eco)
  }

  @Test
  fun allBundledPuzzlesHaveValidFensAndLegalSolutions() {
    for (pz in PuzzleCatalog.puzzles) {
      var st = ChessNotation.fromFen(pz.fen)
      assertNotNull("Invalid FEN in puzzle ${pz.id}", st)
      for ((idx, uci) in pz.solutionUci.withIndex()) {
        val move = ChessNotation.parseUciMove(st!!, uci)
        assertNotNull("Illegal solution move $uci at step $idx in ${pz.id}", move)
        st = ChessRules.applyMove(st, move!!)
      }
    }
  }

  @Test
  fun botReturnsLegalMoveInLowMemoryMode() = runBlocking {
    val engine = ChessEngine()
    val state = ChessRules.initialBoardState()
    val budget = DeviceMemoryAdvisor.computeSearchBudget(
      requestedDepth = 3,
      requestedTimeMs = 500L,
      lowMemoryMode = true,
      batterySavingMode = false,
      isLowRamHardware = true,
    )
    val res = engine.findBestMove(state, budget, randomnessCp = 0)
    assertNotNull(res)
    val legal = ChessRules.legalMoves(state)
    assertTrue(res!!.bestMove in legal)
    assertEquals(PieceColor.WHITE, state.turn)
  }

  @Test
  fun uciLineParserParsesInfoCpMateAndBestMove() {
    val cpInfo = UciLineParser.parseInfoLine(
      "info depth 12 seldepth 18 multipv 1 score cp 42 nodes 15432 nps 80000 pv e2e4 e7e5 g1f3"
    )
    assertNotNull(cpInfo)
    assertEquals(12, cpInfo?.depth)
    assertEquals(15432, cpInfo?.nodes)
    assertEquals(42, cpInfo?.scoreCpForSideToMove)
    assertNull(cpInfo?.mateForSideToMove)
    assertEquals("e2e4", cpInfo?.pvFirstMove)

    val mateInfo = UciLineParser.parseInfoLine(
      "info depth 8 score mate -3 nodes 4100 pv d8h4"
    )
    assertNotNull(mateInfo)
    assertEquals(8, mateInfo?.depth)
    assertEquals(-3, mateInfo?.mateForSideToMove)
    assertNull(mateInfo?.scoreCpForSideToMove)
    assertEquals("d8h4", mateInfo?.pvFirstMove)

    assertEquals("e2e4", UciLineParser.parseBestMoveLine("bestmove e2e4 ponder e7e5"))
    assertNull(UciLineParser.parseBestMoveLine("bestmove (none)"))
  }

  @Test
  fun eloBotsRankSystemAndThemesCover200ToMaxRange() {
    assertEquals(200, BotCatalog.presets.first().rating)
    assertTrue(BotCatalog.presets.last().rating >= 2600)

    val lowCustom = BotCatalog.botForTargetElo(200)
    val maxCustom = BotCatalog.botForTargetElo(2800)
    assertEquals(200, lowCustom.rating)
    assertEquals(2800, maxCustom.rating)
    assertTrue(lowCustom.randomnessCp > maxCustom.randomnessCp)
    assertTrue(maxCustom.maxDepth > lowCustom.maxDepth)

    assertEquals("NOV", RankSystem.rankForElo(200).badge)
    assertEquals("CLB", RankSystem.rankForElo(1200).badge)
    assertEquals("MAX", RankSystem.rankForElo(2600).badge)
    assertTrue(RankSystem.progressToNextRank(1150) in 0f..1f)

    assertEquals(8, BoardThemeOption.entries.size)
    assertEquals(6, PieceStyleOption.entries.size)
    assertEquals(5, AppAccentOption.entries.size)
  }

  @Test
  fun boardTransitionHelpersResolveCastlingRookAndFlippedGridCoordinates() {
    val fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
    val state = ChessNotation.fromFen(fen)!!

    val whiteKingside = ChessNotation.parseUciMove(state, "e1g1")!!
    val afterWhiteKingside = ChessRules.applyMove(state, whiteKingside)
    val wkRookPath = resolveCastleRookPath(whiteKingside.from, whiteKingside.to, afterWhiteKingside)
    assertNotNull(wkRookPath)
    assertEquals(SquareUtil.fromAlgebraic("h1"), wkRookPath?.fromSq)
    assertEquals(SquareUtil.fromAlgebraic("f1"), wkRookPath?.toSq)

    val whiteQueenside = ChessNotation.parseUciMove(state, "e1c1")!!
    val afterWhiteQueenside = ChessRules.applyMove(state, whiteQueenside)
    val wqRookPath = resolveCastleRookPath(whiteQueenside.from, whiteQueenside.to, afterWhiteQueenside)
    assertNotNull(wqRookPath)
    assertEquals(SquareUtil.fromAlgebraic("a1"), wqRookPath?.fromSq)
    assertEquals(SquareUtil.fromAlgebraic("d1"), wqRookPath?.toSq)

    val nonCastle = ChessNotation.parseUciMove(state, "e1e2")!!
    val afterNonCastle = ChessRules.applyMove(state, nonCastle)
    assertNull(resolveCastleRookPath(nonCastle.from, nonCastle.to, afterNonCastle))

    val e4Grid = squareGridOffset(SquareUtil.fromAlgebraic("e4"))
    val normalTopLeft = gridOffsetToTopLeft(e4Grid, sqSize = 100f, isFlipped = false)
    assertEquals(400f, normalTopLeft.x, 0.01f)
    assertEquals(400f, normalTopLeft.y, 0.01f)

    val flippedTopLeft = gridOffsetToTopLeft(e4Grid, sqSize = 100f, isFlipped = true)
    assertEquals(300f, flippedTopLeft.x, 0.01f)
    assertEquals(300f, flippedTopLeft.y, 0.01f)
  }

  @Test
  fun validMoveIndicatorsRespectCheckPinsDoubleCheckAndEnPassantConstraints() {
    // 1. White king on e1 is in check from black rook on e8; white bishop on c3 can only block on e5,
    // white knight on a1 has zero valid moves, white king cannot stay on e-file.
    val checkFen = "4r2k/8/8/8/8/2B5/8/N3K3 w - - 0 1"
    val checkState = ChessNotation.fromFen(checkFen)!!
    val checkLegal = ChessRules.legalMoves(checkState)

    val bishopTargets = legalTargetMovesForSquare(SquareUtil.fromAlgebraic("c3"), checkLegal)
    assertEquals(setOf(SquareUtil.fromAlgebraic("e5")), bishopTargets.keys)

    val knightTargets = legalTargetMovesForSquare(SquareUtil.fromAlgebraic("a1"), checkLegal)
    assertTrue("Uninvolved knight must have zero valid moves while in check", knightTargets.isEmpty())

    val kingTargets = legalTargetMovesForSquare(SquareUtil.fromAlgebraic("e1"), checkLegal)
    assertTrue(SquareUtil.fromAlgebraic("e2") !in kingTargets)
    assertTrue(SquareUtil.fromAlgebraic("d1") in kingTargets)
    assertTrue(SquareUtil.fromAlgebraic("f1") in kingTargets)

    // 2. Double check (rook on e8 + bishop on b4): only king moves are valid
    val doubleCheckFen = "4r2k/8/8/8/1b6/2B5/8/4K2R w - - 0 1"
    val doubleCheckState = ChessNotation.fromFen(doubleCheckFen)!!
    val doubleCheckLegal = ChessRules.legalMoves(doubleCheckState)
    assertTrue(legalTargetMovesForSquare(SquareUtil.fromAlgebraic("c3"), doubleCheckLegal).isEmpty())
    assertTrue(legalTargetMovesForSquare(SquareUtil.fromAlgebraic("h1"), doubleCheckLegal).isEmpty())
    assertTrue(legalTargetMovesForSquare(SquareUtil.fromAlgebraic("e1"), doubleCheckLegal).isNotEmpty())

    // 3. Pinned rook on e2 (black rook on e8 pinning e2 to e1): can only move along e-file
    val pinFen = "4r2k/8/8/8/8/8/4R3/4K3 w - - 0 1"
    val pinState = ChessNotation.fromFen(pinFen)!!
    val pinLegal = ChessRules.legalMoves(pinState)
    val pinnedRookTargets = legalTargetMovesForSquare(SquareUtil.fromAlgebraic("e2"), pinLegal)
    assertTrue(SquareUtil.fromAlgebraic("d2") !in pinnedRookTargets)
    assertTrue(SquareUtil.fromAlgebraic("f2") !in pinnedRookTargets)
    assertTrue(SquareUtil.fromAlgebraic("e3") in pinnedRookTargets)
    assertTrue(SquareUtil.fromAlgebraic("e8") in pinnedRookTargets)

    // 4. Horizontal en passant pin (White king on a5, white pawn on d5, black pawn on e5 just pushed, black rook on h5)
    val epPinFen = "7k/8/8/K2Pp2r/8/8/8/8 w - e6 0 1"
    val epPinState = ChessNotation.fromFen(epPinFen)!!
    val epPinLegal = ChessRules.legalMoves(epPinState)
    val d5Targets = legalTargetMovesForSquare(SquareUtil.fromAlgebraic("d5"), epPinLegal)
    assertTrue("Horizontal EP pin must forbid d5xe6", SquareUtil.fromAlgebraic("e6") !in d5Targets)
    assertTrue("Advancing d5->d6 must still be valid", SquareUtil.fromAlgebraic("d6") in d5Targets)
  }

  @Test
  fun botDialogueEngineGeneratesContextualLinesAcrossTiers() {
    val beginner = BotCatalog.presets.first()
    val master = BotCatalog.presets.last()

    assertTrue(BotDialogueEngine.greetingFor(beginner).isNotBlank())
    assertTrue(BotDialogueEngine.onGameStart(master, botPlaysWhite = false).isNotBlank())

    val checkLine = BotDialogueEngine.onMovePlayed(
      bot = master,
      move = Move(from = SquareUtil.fromAlgebraic("d1"), to = SquareUtil.fromAlgebraic("h5")),
      byBot = true,
      isCheck = true,
      moveNumber = 4,
    )
    assertNotNull(checkLine)
    assertTrue(checkLine!!.contains("Check", ignoreCase = true))

    val drawDeclined = BotDialogueEngine.onDrawDeclined(beginner)
    assertTrue(drawDeclined.isNotBlank())

    val winLine = BotDialogueEngine.onGameFinished(
      bot = beginner,
      result = GameResult.WHITE_WIN,
      termination = TerminationReason.CHECKMATE,
      botPlayedWhite = false,
    )
    assertTrue(winLine.isNotBlank())
  }
}
