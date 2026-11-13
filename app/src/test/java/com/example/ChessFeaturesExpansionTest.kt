package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analysis.AnalysisCoordinator
import com.example.analysis.MoveAnalysisEntry
import com.example.analysis.MoveClassification
import com.example.badges.GameBadgeSystem
import com.example.bots.BotCatalog
import com.example.bots.BotDialogueEngine
import com.example.chess.CastlingMask
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.OpeningBook
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.SquareUtil
import com.example.database.AppSettingsEntity
import com.example.database.BotStatsEntity
import com.example.database.ChessHubDatabase
import com.example.database.ChessHubRepository
import com.example.offline.TimeCategory
import com.example.offline.TimeControl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChessFeaturesExpansionTest {

  @Test
  fun chess960PositionsRespectFischerRandomInvariantsAndHaveLegalMoves() {
    val seeds = listOf(0, 17, 42, 518, 777, 959)
    for (seed in seeds) {
      val state = ChessRules.chess960BoardState(seed)
      val whiteBack = (0..7).map { SquareUtil.of(it, 0) }
      val bishopFiles = whiteBack.filter { PieceCode.typeOf(state.board[it]) == PieceType.BISHOP }.map { SquareUtil.fileOf(it) }
      assertEquals("Seed $seed should have 2 bishops", 2, bishopFiles.size)
      assertTrue("Seed $seed bishops must be opposite colors", (bishopFiles[0] % 2) != (bishopFiles[1] % 2))

      val rookFiles = whiteBack.filter { PieceCode.typeOf(state.board[it]) == PieceType.ROOK }.map { SquareUtil.fileOf(it) }.sorted()
      val kingFile = whiteBack.indexOfFirst { PieceCode.typeOf(state.board[it]) == PieceType.KING }
      assertEquals("Seed $seed should have 2 rooks", 2, rookFiles.size)
      assertTrue("Seed $seed king must sit between rooks", kingFile in (rookFiles[0] + 1) until rookFiles[1])

      val legal = ChessRules.legalMoves(state)
      assertTrue("Seed $seed must have legal opening moves", legal.size >= 16)
    }
  }

  @Test
  fun handicapBoardRemovesPieceAndUpdatesCastling() {
    val whiteQueenOdds = ChessRules.handicapBoardState(PieceType.QUEEN, removeFromWhite = true)
    assertEquals(PieceCode.EMPTY, whiteQueenOdds.board[SquareUtil.of(3, 0)])
    assertEquals(PieceCode.BQ, whiteQueenOdds.board[SquareUtil.of(3, 7)])

    val blackRookOdds = ChessRules.handicapBoardState(PieceType.ROOK, removeFromWhite = false)
    assertEquals(PieceCode.EMPTY, blackRookOdds.board[SquareUtil.of(0, 7)])
    assertEquals(0, blackRookOdds.castlingMask and CastlingMask.BLACK_QUEEN)
    assertTrue(ChessRules.legalMoves(blackRookOdds).isNotEmpty())
  }

  @Test
  fun allOpeningRepertoiresAndGambitsPlayLegallyFromStartPosition() {
    val repertoires = OpeningBook.allRepertoires()
    assertTrue("Should expose comprehensive opening & gambit library", repertoires.size >= 25)
    assertTrue("Should have multiple Gambits", OpeningBook.allGambits().size >= 10)

    for (rep in repertoires) {
      var state = ChessRules.initialBoardState()
      for ((idx, san) in rep.movesTokens.withIndex()) {
        val legal = ChessRules.legalMoves(state)
        val matched = legal.firstOrNull { move ->
          ChessNotation.toSan(state, move).trimEnd('+', '#') == san.trimEnd('+', '#')
        }
        assertNotNull("Repertoire '${rep.name}' token #$idx '$san' must be legal", matched)
        state = ChessRules.applyMove(state, matched!!)
      }
      val recognized = OpeningBook.recognize(rep.movesTokens)
      assertNotNull("Repertoire '${rep.name}' should resolve in OpeningBook.recognize", recognized)
      assertTrue("Plan summary should be populated for '${rep.name}'", recognized!!.planSummary.isNotBlank())
    }
  }

  @Test
  fun allBotsHaveUniqueProfilesAvatarsAndOpeningDialogue() {
    assertEquals(11, BotCatalog.presets.size)
    val catchphrases = mutableSetOf<String>()
    val opening = OpeningBook.recognize(listOf("e4", "e5", "f4"))!! // King's Gambit
    assertTrue(opening.isGambit)

    for (bot in BotCatalog.presets) {
      assertTrue("${bot.name} missing nationality", bot.nationality.isNotBlank())
      assertTrue("${bot.name} missing favoriteOpening", bot.favoriteOpening.isNotBlank())
      assertTrue("${bot.name} missing bio", bot.bio.isNotBlank())
      assertTrue("${bot.name} missing catchphrase", bot.catchphrase.isNotBlank())
      catchphrases.add(bot.catchphrase)

      val greeting = BotDialogueEngine.onGameStart(bot, botPlaysWhite = false)
      assertTrue("${bot.name} greeting should be non-blank", greeting.isNotBlank())

      val openingLine = BotDialogueEngine.onOpeningDetected(bot, opening)
      assertTrue("Opening dialogue should reference the gambit name", openingLine.contains("King's Gambit"))
    }
    assertEquals("All 11 bots should have distinct catchphrases", 11, catchphrases.size)

    val customBot = BotCatalog.botForTargetElo(2450)
    assertEquals(2450, customBot.rating)
    assertTrue(BotDialogueEngine.onOpeningDetected(customBot, opening).contains("King's Gambit"))
  }

  @Test
  fun timeControlsCoverBulletBlitzRapidClassicalAndUnlimited() {
    assertEquals(TimeCategory.BULLET, TimeControl.presets.first { it.label == "1+0" }.category)
    assertEquals(TimeCategory.BLITZ, TimeControl.presets.first { it.label == "3+2" }.category)
    assertEquals(TimeCategory.RAPID, TimeControl.presets.first { it.label == "10+0" }.category)
    assertEquals(TimeCategory.CLASSICAL, TimeControl.presets.first { it.label == "30+0" }.category)
    assertEquals(TimeCategory.UNLIMITED, TimeControl.presets.first { it.isUnlimited }.category)
    assertTrue(TimeControl.presetsByCategory(TimeCategory.BULLET).isNotEmpty())
    assertTrue(TimeControl.presetsByCategory(TimeCategory.BLITZ).isNotEmpty())
    assertTrue(TimeControl.presetsByCategory(TimeCategory.RAPID).isNotEmpty())
  }

  @Test
  fun sideFeedbackComparisonComputesCountsAndAcplForBothPlayers() {
    val start = ChessRules.initialBoardState()
    val e4 = ChessNotation.parseUciMove(start, "e2e4")!!
    val entries = listOf(
      MoveAnalysisEntry(1, "e4", "e2e4", e4, PieceColor.WHITE, 0, 20, 0, null, MoveClassification.BOOK, "e4", "e2e4", false, "Book"),
      MoveAnalysisEntry(2, "e5", "e7e5", e4, PieceColor.BLACK, 20, 15, 0, null, MoveClassification.BEST, "e5", "e7e5", false, "Best"),
      MoveAnalysisEntry(3, "Qh5", "d1h5", e4, PieceColor.WHITE, 15, -90, -105, null, MoveClassification.MISTAKE, "Nf3", "g1f3", true, "Mistake"),
      MoveAnalysisEntry(4, "g6", "g7g6", e4, PieceColor.BLACK, -90, -290, -200, null, MoveClassification.BLUNDER, "Nc6", "b8c6", true, "Blunder"),
    )

    val whiteCounts = AnalysisCoordinator.summarizeSide(entries.filter { it.mover == PieceColor.WHITE })
    val blackCounts = AnalysisCoordinator.summarizeSide(entries.filter { it.mover == PieceColor.BLACK })

    assertEquals(2, whiteCounts.totalMoves)
    assertEquals(1, whiteCounts.book)
    assertEquals(1, whiteCounts.mistake)
    assertEquals(52, whiteCounts.averageCpLoss)

    assertEquals(2, blackCounts.totalMoves)
    assertEquals(1, blackCounts.best)
    assertEquals(1, blackCounts.blunder)
    assertEquals(100, blackCounts.averageCpLoss)
  }

  @Test
  fun gameBadgeSystemUnlocksExpectedMilestonesAndPersistsInRoomV4() {
    val settings = AppSettingsEntity(
      localWins = 1,
      localRating = 1420,
      bookDrillsCompleted = 3,
      gameReviewsCompleted = 1,
      puzzleStreak = 5,
      unlockedBadgesCsv = "gambit_daredevil,custom_architect",
    )
    val stats = listOf(BotStatsEntity(botId = "bot_master", playerWins = 1))
    val evaluated = GameBadgeSystem.evaluate(settings, stats).associate { it.badge.id to it.unlocked }

    assertTrue(evaluated["first_win"] == true)
    assertTrue(evaluated["giant_slayer"] == true)
    assertTrue(evaluated["master_challenger"] == true)
    assertTrue(evaluated["book_scholar"] == true)
    assertTrue(evaluated["game_analyst"] == true)
    assertTrue(evaluated["tactician"] == true)
    assertTrue(evaluated["gambit_daredevil"] == true)
    assertTrue(evaluated["custom_architect"] == true)
    assertFalse(evaluated["speed_demon"] == true)

    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = Room.inMemoryDatabaseBuilder(context, ChessHubDatabase::class.java)
        .allowMainThreadQueries()
        .build()
      val repo = ChessHubRepository(db, defaultLowMemory = true)
      repo.ensureInitializedSettings()
      repo.updateSettings {
        it.copy(
          unlockedBadgesCsv = "first_win,gambit_daredevil",
          bookDrillsCompleted = 4,
          gameReviewsCompleted = 2,
        )
      }
      val reloaded = repo.ensureInitializedSettings()
      assertEquals("first_win,gambit_daredevil", reloaded.unlockedBadgesCsv)
      assertEquals(4, reloaded.bookDrillsCompleted)
      assertEquals(2, reloaded.gameReviewsCompleted)
      db.close()
    }
  }
}
