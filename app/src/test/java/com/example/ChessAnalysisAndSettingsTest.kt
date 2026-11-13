package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analysis.AnalysisCoordinator
import com.example.analysis.AnalysisPolicy
import com.example.analysis.MoveClassification
import com.example.analysis.MoveFeedbackAnalyzer
import com.example.bots.BotCatalog
import com.example.chess.BotPlayingStyle
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.OpeningBook
import com.example.chess.RankSystem
import com.example.database.AppSettingsEntity
import com.example.database.ChessHubDatabase
import com.example.database.ChessHubRepository
import com.example.engine.DeviceMemoryAdvisor
import com.example.engine.EngineAnalysisOutput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChessAnalysisAndSettingsTest {

  @Test
  fun moveFeedbackAnalyzerClassifiesAllMoveTiersAndAccuracyCorrectly() {
    val start = ChessRules.initialBoardState()
    val e4Move = ChessNotation.parseUciMove(start, "e2e4")!!
    val afterE4 = ChessRules.applyMove(start, e4Move)

    val bookEntry = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 1,
      stateBefore = start,
      move = e4Move,
      san = "e4",
      stateAfter = afterE4,
      evalBeforeCpForWhite = 20,
      evalAfterCpForWhite = 25,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 25,
      secondBestEvalBeforeForMoverCp = 10,
      isBookMove = true,
      pvSanList = listOf("e5", "Nf3"),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.BOOK, bookEntry.classification)
    assertEquals(100.0, bookEntry.moveAccuracyPercent, 0.01)

    val bestEntry = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 5,
      stateBefore = start,
      move = e4Move,
      san = "e4",
      stateAfter = afterE4,
      evalBeforeCpForWhite = 20,
      evalAfterCpForWhite = 30,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 30,
      secondBestEvalBeforeForMoverCp = 5,
      isBookMove = false,
      pvSanList = listOf("e5"),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.BEST, bestEntry.classification)
    assertTrue(bestEntry.explanation.isNotBlank())

    val d4Move = ChessNotation.parseUciMove(start, "d2d4")!!
    val afterD4 = ChessRules.applyMove(start, d4Move)
    val inaccuracy = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 12,
      stateBefore = start,
      move = d4Move,
      san = "d4",
      stateAfter = afterD4,
      evalBeforeCpForWhite = 100,
      evalAfterCpForWhite = -40,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 100,
      secondBestEvalBeforeForMoverCp = -40,
      isBookMove = false,
      pvSanList = emptyList(),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.INACCURACY, inaccuracy.classification)
    assertEquals("e4", inaccuracy.bestMoveSan)

    val mistake = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 14,
      stateBefore = start,
      move = d4Move,
      san = "d4",
      stateAfter = afterD4,
      evalBeforeCpForWhite = 100,
      evalAfterCpForWhite = -160,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 100,
      secondBestEvalBeforeForMoverCp = -160,
      isBookMove = false,
      pvSanList = emptyList(),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.MISTAKE, mistake.classification)

    val blunder = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 16,
      stateBefore = start,
      move = d4Move,
      san = "d4",
      stateAfter = afterD4,
      evalBeforeCpForWhite = 120,
      evalAfterCpForWhite = -420,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 120,
      secondBestEvalBeforeForMoverCp = -420,
      isBookMove = false,
      pvSanList = emptyList(),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.BLUNDER, blunder.classification)
    assertTrue(blunder.isCriticalMoment)

    val missed = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 18,
      stateBefore = start,
      move = d4Move,
      san = "d4",
      stateAfter = afterD4,
      evalBeforeCpForWhite = 30,
      evalAfterCpForWhite = 40,
      mateInAfterForWhite = null,
      bestMoveInBefore = e4Move,
      bestMoveSan = "e4",
      bestEvalBeforeForMoverCp = 240,
      secondBestEvalBeforeForMoverCp = 40,
      isBookMove = false,
      pvSanList = emptyList(),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.MISSED_OPPORTUNITY, missed.classification)
    assertTrue(missed.isCriticalMoment)

    // Brilliant sacrifice: Bxf7+ in Italian-style position where Bishop (330) captures pawn (100) on defended f7
    val sacFen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQ1RK1 w kq - 0 6"
    val sacBefore = ChessNotation.fromFen(sacFen)!!
    val bxf7 = ChessNotation.parseUciMove(sacBefore, "c4f7")!!
    val sacAfter = ChessRules.applyMove(sacBefore, bxf7)
    val brilliant = MoveFeedbackAnalyzer.evaluateMoveFeedback(
      plyIndex = 11,
      stateBefore = sacBefore,
      move = bxf7,
      san = "Bxf7+",
      stateAfter = sacAfter,
      evalBeforeCpForWhite = 120,
      evalAfterCpForWhite = 180,
      mateInAfterForWhite = null,
      bestMoveInBefore = bxf7,
      bestMoveSan = "Bxf7+",
      bestEvalBeforeForMoverCp = 180,
      secondBestEvalBeforeForMoverCp = 10,
      isBookMove = false,
      pvSanList = listOf("Kxf7", "Ng5+"),
      enableCriticalMoments = true,
    )
    assertEquals(MoveClassification.BRILLIANT, brilliant.classification)
    assertTrue(brilliant.isCriticalMoment)

    // Win probability monotonicity
    assertTrue(MoveFeedbackAnalyzer.winProbabilityPercent(300) > MoveFeedbackAnalyzer.winProbabilityPercent(0))
    assertTrue(MoveFeedbackAnalyzer.moveAccuracyPercent(60.0, 60.0) >= 99.0)
    assertTrue(MoveFeedbackAnalyzer.moveAccuracyPercent(80.0, 30.0) < 40.0)
  }

  @Test
  fun endgameValidatorAndTrainerDrillsWorkAccurately() {
    val kpEndgame = ChessNotation.fromFen("8/8/4k3/8/4P3/4K3/8/8 w - - 0 1")!!
    val kpInfo = MoveFeedbackAnalyzer.analyzeEndgame(kpEndgame)
    assertTrue(kpInfo.isEndgame)
    assertEquals("King & Pawn Endgame", kpInfo.category)

    val rookEndgame = ChessNotation.fromFen("1K1k4/1P6/8/8/8/8/r7/4R3 w - - 0 1")!!
    val rookInfo = MoveFeedbackAnalyzer.analyzeEndgame(rookEndgame)
    assertTrue(rookInfo.isEndgame)
    assertEquals("Rook Endgame", rookInfo.category)

    assertNull(MoveFeedbackAnalyzer.validateCustomPosition(ChessRules.initialBoardState()))
    assertNotNull(MoveFeedbackAnalyzer.validateCustomPosition(ChessNotation.fromFen("8/8/8/8/4kK2/8/8/8 w - - 0 1")!!))
    assertNotNull(MoveFeedbackAnalyzer.validateCustomPosition(ChessNotation.fromFen("4P3/8/8/8/8/8/4k3/4K3 w - - 0 1")!!))

    for (drill in MoveFeedbackAnalyzer.trainerDrills) {
      val parsed = ChessNotation.fromFen(drill.fen)
      assertNotNull("Trainer drill ${drill.id} has invalid FEN", parsed)
      assertNull("Trainer drill ${drill.id} failed validation", MoveFeedbackAnalyzer.validateCustomPosition(parsed!!))
      assertTrue("Trainer drill ${drill.id} has no legal moves", ChessRules.legalMoves(parsed).isNotEmpty())
    }
  }

  @Test
  fun analysisPolicyHonorsAll13SwitchesAndSkipsEngineWhenDisabled() = runBlocking {
    val allOff = AppSettingsEntity(
      showEvalBar = false,
      showThreatArrow = false,
      showSuggestionArrow = false,
      showEngineLine = false,
      showMoveFeedback = false,
      showBotMoveFeedback = false,
      enableGameAnalysis = false,
      showEvalGraph = false,
      showCandidateMoves = false,
      showTacticalSuggestions = false,
      enableCriticalMoments = false,
      showOpeningInfo = false,
      enableEngineAnalysis = false,
    )

    assertFalse(AnalysisPolicy.shouldRunLiveEngine(allOff))
    assertFalse(AnalysisPolicy.needPv(allOff))
    assertFalse(AnalysisPolicy.needCandidates(allOff))
    assertFalse(AnalysisPolicy.needThreat(allOff))
    assertFalse(AnalysisPolicy.showEvalBar(allOff))
    assertFalse(AnalysisPolicy.showSuggestionArrow(allOff))
    assertFalse(AnalysisPolicy.showThreatArrow(allOff))
    assertFalse(AnalysisPolicy.showMoveFeedbackForGame(allOff, involvesBot = false))
    assertFalse(AnalysisPolicy.showMoveFeedbackForGame(allOff, involvesBot = true))

    // Even if visual flags are ON, disabling master enableEngineAnalysis suppresses engine search
    val masterOff = AppSettingsEntity(enableEngineAnalysis = false, showEvalBar = true, showSuggestionArrow = true)
    assertFalse(AnalysisPolicy.shouldRunLiveEngine(masterOff))
    assertFalse(AnalysisPolicy.showEvalBar(masterOff))
    assertFalse(AnalysisPolicy.showSuggestionArrow(masterOff))

    // Disabling only showBotMoveFeedback hides feedback in bot games while keeping human-game feedback
    val onlyBotFeedbackOff = AppSettingsEntity(showMoveFeedback = true, showBotMoveFeedback = false)
    assertTrue(AnalysisPolicy.showMoveFeedbackForGame(onlyBotFeedbackOff, involvesBot = false))
    assertFalse(AnalysisPolicy.showMoveFeedbackForGame(onlyBotFeedbackOff, involvesBot = true))

    // Verify AnalysisCoordinator makes ZERO calls to runDetailed when live engine is disabled
    var engineCalls = 0
    val memProfile = DeviceMemoryAdvisor.inspect(ApplicationProvider.getApplicationContext())
    val (snapshot, output) = AnalysisCoordinator.buildPositionSnapshot(
      state = ChessRules.initialBoardState(),
      sanMovesSoFar = listOf("e4", "e5"),
      settings = masterOff.copy(showOpeningInfo = true),
      memoryProfile = memProfile,
      requestedDepth = 2,
      requestedTimeMs = 200L,
      runDetailed = { _, _, _, _, _ ->
        engineCalls++
        EngineAnalysisOutput(
          bestMove = null,
          scoreCpForSide = 0,
          mateInForSide = null,
          pvMoves = emptyList(),
          candidates = emptyList(),
          threatMove = null,
          threatSwingCp = 0,
          depthReached = 1,
          nodesVisited = 10,
        )
      },
    )
    assertEquals(0, engineCalls)
    assertNull(output)
    assertTrue("Opening explorer continuations should still populate when showOpeningInfo is true", snapshot.openingContinuations.isNotEmpty())
  }

  @Test
  fun botsReach3200EloWithStylesAndOpeningExplorerReturnsContinuations() {
    assertEquals(200, BotCatalog.MIN_ELO)
    assertEquals(3200, BotCatalog.MAX_ELO)
    assertEquals(3200, BotCatalog.presets.last().rating)

    val custom3200 = BotCatalog.botForTargetElo(3200)
    assertEquals(3200, custom3200.rating)
    assertTrue(BotPlayingStyle.entries.size >= 5)

    val rank3200 = RankSystem.rankForElo(3200)
    assertEquals("OMG", rank3200.badge)

    val afterE4 = OpeningBook.exploreContinuations(listOf("e4"))
    assertTrue(afterE4.isNotEmpty())
    val afterSicilian = OpeningBook.exploreContinuations(listOf("e4", "c5"))
    assertTrue(afterSicilian.isNotEmpty())
  }

  @Test
  fun roomDatabasePersistsAll13AnalysisSettingsSwitches() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, ChessHubDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = ChessHubRepository(db, defaultLowMemory = true)
      val initial = repo.ensureInitializedSettings()
      assertTrue(initial.showEvalBar)
      assertTrue(initial.enableEngineAnalysis)

      val saved = repo.updateSettings {
        it.copy(
          showEvalBar = false,
          showThreatArrow = false,
          showSuggestionArrow = false,
          showEngineLine = false,
          showMoveFeedback = false,
          showBotMoveFeedback = false,
          enableGameAnalysis = false,
          showEvalGraph = false,
          showCandidateMoves = false,
          showTacticalSuggestions = false,
          enableCriticalMoments = false,
          showOpeningInfo = false,
          enableEngineAnalysis = false,
        )
      }

      val reloaded = db.settingsDao().getSettings()!!
      assertEquals(saved, reloaded)
      assertFalse(reloaded.showEvalBar)
      assertFalse(reloaded.showThreatArrow)
      assertFalse(reloaded.showSuggestionArrow)
      assertFalse(reloaded.showEngineLine)
      assertFalse(reloaded.showMoveFeedback)
      assertFalse(reloaded.showBotMoveFeedback)
      assertFalse(reloaded.enableGameAnalysis)
      assertFalse(reloaded.showEvalGraph)
      assertFalse(reloaded.showCandidateMoves)
      assertFalse(reloaded.showTacticalSuggestions)
      assertFalse(reloaded.enableCriticalMoments)
      assertFalse(reloaded.showOpeningInfo)
      assertFalse(reloaded.enableEngineAnalysis)
    } finally {
      db.close()
    }
  }
}
