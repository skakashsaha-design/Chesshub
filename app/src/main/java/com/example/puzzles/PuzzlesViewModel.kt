package com.example.puzzles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PuzzleAttemptRecord
import com.example.PuzzleUiState
import com.example.R
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.Move
import com.example.chess.PieceCode
import com.example.chess.PieceType
import com.example.database.AppSettingsEntity
import com.example.database.ChessHubDatabase
import com.example.database.ChessHubRepository
import com.example.database.PuzzleProgressEntity
import com.example.engine.DeviceMemoryAdvisor
import com.example.offline.SoundAndHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel managing tactical chess puzzle state, user move validation,
 * promotion choices, hints, theme filtering, and per-attempt tracking.
 */
class PuzzlesViewModel(application: Application) : AndroidViewModel(application) {

  private val memoryProfile = DeviceMemoryAdvisor.inspect(application)
  private val db = ChessHubDatabase.getInstance(application)
  private val repository = ChessHubRepository(db, memoryProfile.recommendLowMemoryMode)
  private val soundHaptics = SoundAndHaptics(application)

  val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = AppSettingsEntity(lowMemoryMode = memoryProfile.recommendLowMemoryMode),
    )

  val progressList: StateFlow<List<PuzzleProgressEntity>> = repository.puzzleProgressFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val themeFilters: List<String> = listOf(
    "All",
    "Mate in 1",
    "Mate in 2",
    "Fork",
    "Smothered Mate",
    "Promotion",
    "Skewer & Pin",
  )

  private val _puzzleState = MutableStateFlow(createInitialState(PuzzleCatalog.puzzles.first()))
  val puzzleState: StateFlow<PuzzleUiState> = _puzzleState.asStateFlow()

  fun filteredPuzzles(filter: String = _puzzleState.value.selectedThemeFilter): List<ChessPuzzle> {
    if (filter == "All") return PuzzleCatalog.puzzles
    return PuzzleCatalog.puzzles.filter { pz ->
      when (filter) {
        "Skewer & Pin" -> pz.theme.contains("Skewer", ignoreCase = true) ||
          pz.theme.contains("Pin", ignoreCase = true)
        "Promotion" -> pz.theme.contains("Promotion", ignoreCase = true)
        else -> pz.theme.equals(filter, ignoreCase = true) ||
          pz.theme.contains(filter, ignoreCase = true)
      }
    }.ifEmpty { PuzzleCatalog.puzzles }
  }

  fun selectThemeFilter(theme: String) {
    val candidates = filteredPuzzles(theme)
    val current = _puzzleState.value.puzzle
    if (current in candidates) {
      _puzzleState.update { it.copy(selectedThemeFilter = theme) }
    } else {
      val next = candidates.first()
      _puzzleState.value = createInitialState(
        puzzle = next,
        themeFilter = theme,
        sessionAttempts = _puzzleState.value.sessionAttempts,
      )
    }
  }

  fun loadNextOrRandomPuzzle() {
    val curState = _puzzleState.value
    val pool = filteredPuzzles(curState.selectedThemeFilter)
    val solvedIds = progressList.value.filter { it.solved }.map { it.puzzleId }.toSet()
    val candidate = if (curState.isSolved) {
      val unsolved = pool.filter { it.id != curState.puzzle.id && it.id !in solvedIds }
      val others = pool.filter { it.id != curState.puzzle.id }
      (unsolved.ifEmpty { others.ifEmpty { pool } }).first()
    } else {
      val others = pool.filter { it.id != curState.puzzle.id }
      (others.ifEmpty { pool }).random()
    }
    _puzzleState.value = createInitialState(
      puzzle = candidate,
      themeFilter = curState.selectedThemeFilter,
      sessionAttempts = curState.sessionAttempts,
    )
  }

  fun loadPuzzleById(id: String) {
    val found = PuzzleCatalog.puzzles.firstOrNull { it.id == id } ?: return
    val cur = _puzzleState.value
    _puzzleState.value = createInitialState(
      puzzle = found,
      themeFilter = cur.selectedThemeFilter,
      sessionAttempts = cur.sessionAttempts,
    )
  }

  fun retryCurrentPuzzle() {
    val cur = _puzzleState.value
    _puzzleState.value = createInitialState(
      puzzle = cur.puzzle,
      themeFilter = cur.selectedThemeFilter,
      sessionAttempts = cur.sessionAttempts,
    )
  }

  fun revealHint() {
    val p = _puzzleState.value
    val expectedUci = p.puzzle.solutionUci.getOrNull(p.stepIndex)
    val hint = expectedUci?.let { ChessNotation.parseUciMove(p.boardState, it) }
    _puzzleState.update {
      it.copy(
        showHint = true,
        hintMove = hint,
      )
    }
  }

  fun onSquareTapped(square: Int) {
    val p = _puzzleState.value
    if (p.isSolved) return
    if (p.pendingPromotionFrom != null) return

    val sel = p.selectedSquare
    if (sel != null) {
      val candidates = p.legalMoves.filter { it.from == sel && it.to == square }
      if (candidates.isNotEmpty()) {
        if (candidates.size > 1 && candidates.any { it.promotion != null }) {
          _puzzleState.update {
            it.copy(
              pendingPromotionFrom = sel,
              pendingPromotionTo = square,
              selectedSquare = null,
            )
          }
          return
        }
        evaluateAttempt(candidates.first())
        return
      }
    }

    val piece = p.boardState.board[square]
    if (piece != PieceCode.EMPTY && PieceCode.colorOf(piece) == p.boardState.turn) {
      _puzzleState.update { it.copy(selectedSquare = square) }
    } else {
      _puzzleState.update { it.copy(selectedSquare = null) }
    }
  }

  fun onPromotionSelected(pieceType: PieceType) {
    val p = _puzzleState.value
    val from = p.pendingPromotionFrom ?: return
    val to = p.pendingPromotionTo ?: return
    val chosen = p.legalMoves.firstOrNull {
      it.from == from && it.to == to && it.promotion == pieceType
    } ?: return
    evaluateAttempt(chosen)
  }

  fun dismissPromotion() {
    _puzzleState.update {
      it.copy(pendingPromotionFrom = null, pendingPromotionTo = null)
    }
  }

  private fun evaluateAttempt(move: Move) {
    val p = _puzzleState.value
    val expectedUci = p.puzzle.solutionUci.getOrNull(p.stepIndex) ?: return
    val nextAttempts = p.attemptsOnCurrent + 1

    if (move.toUci() == expectedUci) {
      applyCorrectMove(move, nextAttempts)
    } else {
      val nextWrong = p.wrongAttemptsOnCurrent + 1
      val s = settings.value
      soundHaptics.hapticTap(s, strong = true)
      val attemptEntry = PuzzleAttemptRecord(
        puzzleId = p.puzzle.id,
        title = p.puzzle.title,
        theme = p.puzzle.theme,
        rating = p.puzzle.rating,
        solved = false,
        solvedCleanly = false,
        attemptsCount = nextAttempts,
        wrongCount = nextWrong,
      )
      val updatedLog = updateAttemptLog(p.sessionAttempts, attemptEntry)
      _puzzleState.update {
        it.copy(
          selectedSquare = null,
          pendingPromotionFrom = null,
          pendingPromotionTo = null,
          hadMistake = true,
          attemptsOnCurrent = nextAttempts,
          wrongAttemptsOnCurrent = nextWrong,
          statusMessage = getApplication<Application>().getString(R.string.puzzles_wrong),
          sessionAttempts = updatedLog,
        )
      }
    }
  }

  private fun applyCorrectMove(move: Move, nextAttempts: Int) {
    val p = _puzzleState.value
    var nextBoard = ChessRules.applyMove(p.boardState, move)
    var nextStep = p.stepIndex + 1
    var lastFrom = move.from
    var lastTo = move.to

    // Auto-play forced opponent reply if present
    if (nextStep < p.puzzle.solutionUci.size) {
      val replyUci = p.puzzle.solutionUci[nextStep]
      val replyMove = ChessNotation.parseUciMove(nextBoard, replyUci)
      if (replyMove != null) {
        nextBoard = ChessRules.applyMove(nextBoard, replyMove)
        lastFrom = replyMove.from
        lastTo = replyMove.to
        nextStep++
      }
    }

    val solved = nextStep >= p.puzzle.solutionUci.size
    val solvedCleanly = solved && !p.hadMistake
    val s = settings.value
    val isCheckAfterMove = ChessRules.isInCheck(nextBoard)
    val isCaptureMove = move.capturedPiece != PieceCode.EMPTY || move.isEnPassant
    when {
      solved -> {
        soundHaptics.playGameEndSound(s)
        soundHaptics.hapticTap(s, strong = true)
        viewModelScope.launch {
          repository.recordPuzzleOutcome(p.puzzle.id, p.puzzle.rating, solvedCleanly)
        }
      }
      isCheckAfterMove -> {
        soundHaptics.playCheckSound(s)
        soundHaptics.hapticTap(s, strong = true)
      }
      isCaptureMove -> {
        soundHaptics.playCaptureSound(s)
        soundHaptics.hapticTap(s)
      }
      else -> {
        soundHaptics.playMoveSound(s)
        soundHaptics.hapticTap(s)
      }
    }

    val updatedLog = if (solved) {
      val entry = PuzzleAttemptRecord(
        puzzleId = p.puzzle.id,
        title = p.puzzle.title,
        theme = p.puzzle.theme,
        rating = p.puzzle.rating,
        solved = true,
        solvedCleanly = solvedCleanly,
        attemptsCount = nextAttempts,
        wrongCount = p.wrongAttemptsOnCurrent,
      )
      updateAttemptLog(p.sessionAttempts, entry)
    } else {
      p.sessionAttempts
    }

    val statusText = when {
      solvedCleanly -> getApplication<Application>().getString(R.string.puzzles_solved_clean)
      solved -> getApplication<Application>().getString(R.string.puzzles_solved_msg)
      else -> getApplication<Application>().getString(R.string.puzzles_correct)
    }

    _puzzleState.update {
      it.copy(
        boardState = nextBoard,
        stepIndex = nextStep,
        selectedSquare = null,
        pendingPromotionFrom = null,
        pendingPromotionTo = null,
        legalMoves = if (solved) emptyList() else ChessRules.legalMoves(nextBoard),
        lastMoveFrom = lastFrom,
        lastMoveTo = lastTo,
        hintMove = null,
        isSolved = solved,
        attemptsOnCurrent = nextAttempts,
        statusMessage = statusText,
        sessionAttempts = updatedLog,
      )
    }
  }

  private fun updateAttemptLog(
    existing: List<PuzzleAttemptRecord>,
    latest: PuzzleAttemptRecord,
  ): List<PuzzleAttemptRecord> {
    val withoutSameInProgress = existing.filterNot {
      it.puzzleId == latest.puzzleId && !it.solved
    }
    return (listOf(latest) + withoutSameInProgress).take(30)
  }

  private fun createInitialState(
    puzzle: ChessPuzzle,
    themeFilter: String = "All",
    sessionAttempts: List<PuzzleAttemptRecord> = emptyList(),
  ): PuzzleUiState {
    val board = ChessNotation.fromFen(puzzle.fen) ?: ChessRules.initialBoardState()
    val playerMovesCount = (puzzle.solutionUci.size + 1) / 2
    return PuzzleUiState(
      puzzle = puzzle,
      boardState = board,
      playerColor = board.turn,
      stepIndex = 0,
      totalPlayerMoves = playerMovesCount,
      legalMoves = ChessRules.legalMoves(board),
      selectedThemeFilter = themeFilter,
      sessionAttempts = sessionAttempts,
    )
  }

  override fun onCleared() {
    soundHaptics.release()
    super.onCleared()
  }
}
