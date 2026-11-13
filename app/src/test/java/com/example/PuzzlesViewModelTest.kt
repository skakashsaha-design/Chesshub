package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.chess.ChessNotation
import com.example.chess.PieceColor
import com.example.chess.PieceType
import com.example.chess.SquareUtil
import com.example.puzzles.PuzzlesViewModel
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
class PuzzlesViewModelTest {

  private fun sq(algebraic: String): Int =
    SquareUtil.of(algebraic[0] - 'a', algebraic[1] - '1')

  @Test
  fun `wrong attempt updates attempts counter and session log`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = PuzzlesViewModel(app)

    // pz_01 solution is d1d8; play d1d7 instead
    vm.onSquareTapped(sq("d1"))
    assertEquals(sq("d1"), vm.puzzleState.value.selectedSquare)

    vm.onSquareTapped(sq("d7"))
    val st = vm.puzzleState.value
    assertFalse(st.isSolved)
    assertTrue(st.hadMistake)
    assertEquals(1, st.attemptsOnCurrent)
    assertEquals(1, st.wrongAttemptsOnCurrent)
    assertEquals(1, st.sessionAttempts.size)
    assertFalse(st.sessionAttempts.first().solved)
  }

  @Test
  fun `hint reveals text and move squares while multi-move puzzle auto-plays opponent reply`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = PuzzlesViewModel(app)

    // pz_06: 1.h5f7 g8h8 2.f7e8
    vm.loadPuzzleById("pz_06")
    assertEquals(2, vm.puzzleState.value.totalPlayerMoves)

    vm.revealHint()
    assertTrue(vm.puzzleState.value.showHint)
    assertNotNull(vm.puzzleState.value.hintMove)
    assertEquals("h5f7", vm.puzzleState.value.hintMove?.toUci())

    vm.onSquareTapped(sq("h5"))
    vm.onSquareTapped(sq("f7"))

    val mid = vm.puzzleState.value
    assertFalse(mid.isSolved)
    assertEquals(2, mid.stepIndex) // player h5f7 + forced g8h8 reply
    assertNull(mid.hintMove)
    assertEquals(1, mid.attemptsOnCurrent)

    vm.onSquareTapped(sq("f7"))
    vm.onSquareTapped(sq("e8"))
    val done = vm.puzzleState.value
    assertTrue(done.isSolved)
    assertEquals(2, done.attemptsOnCurrent)
    assertEquals(0, done.wrongAttemptsOnCurrent)
    assertTrue(done.sessionAttempts.first().solvedCleanly)
  }

  @Test
  fun `underpromotion puzzle opens promotion picker and validates chosen piece`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = PuzzlesViewModel(app)

    // pz_13 requires f7g8n (Knight underpromotion)
    vm.loadPuzzleById("pz_13")
    vm.onSquareTapped(sq("f7"))
    vm.onSquareTapped(sq("g8"))

    assertNotNull(vm.puzzleState.value.pendingPromotionFrom)
    assertNotNull(vm.puzzleState.value.pendingPromotionTo)

    vm.onPromotionSelected(PieceType.KNIGHT)
    assertTrue(vm.puzzleState.value.isSolved)
    assertEquals(1, vm.puzzleState.value.attemptsOnCurrent)
  }

  @Test
  fun `theme filter and retry reset attempt state properly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = PuzzlesViewModel(app)

    vm.selectThemeFilter("Fork")
    val st = vm.puzzleState.value
    assertEquals("Fork", st.selectedThemeFilter)
    assertTrue(st.puzzle.theme.contains("Fork", ignoreCase = true))

    // pz_18 is Black to move
    vm.loadPuzzleById("pz_18")
    assertEquals(PieceColor.BLACK, vm.puzzleState.value.playerColor)

    vm.onSquareTapped(sq("e5"))
    vm.onSquareTapped(sq("c4"))
    assertEquals(1, vm.puzzleState.value.wrongAttemptsOnCurrent)

    vm.retryCurrentPuzzle()
    assertEquals(0, vm.puzzleState.value.attemptsOnCurrent)
    assertEquals(0, vm.puzzleState.value.wrongAttemptsOnCurrent)
    assertFalse(vm.puzzleState.value.hadMistake)
  }
}
