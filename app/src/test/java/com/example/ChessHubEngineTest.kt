package com.example

import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.GameResult
import com.example.chess.OpeningBook
import com.example.chess.PgnCodec
import com.example.chess.PieceColor
import com.example.chess.TerminationReason
import com.example.engine.ChessEngine
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.UciLineParser
import com.example.puzzles.PuzzleCatalog
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
}
