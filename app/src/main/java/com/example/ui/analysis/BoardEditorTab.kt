package com.example.ui.analysis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.MoveFeedbackAnalyzer
import com.example.bots.BotCatalog
import com.example.chess.BoardState
import com.example.chess.BotProfile
import com.example.chess.CastlingMask
import com.example.chess.ChessNotation
import com.example.chess.ChessRules
import com.example.chess.PieceCode
import com.example.chess.PieceColor
import com.example.database.AppSettingsEntity
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.ChessBoardView
import com.example.ui.board.PieceStyleOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardEditorTab(
  settings: AppSettingsEntity,
  initialFen: String,
  onAnalyzeFen: (String) -> Boolean,
  onPlayFenVsBot: (String, BotProfile?) -> Boolean,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var editorState by remember {
    mutableStateOf(ChessNotation.fromFen(initialFen) ?: ChessRules.initialBoardState())
  }
  var fenInput by remember(editorState) { mutableStateOf(ChessNotation.toFen(editorState)) }
  var selectedBrushPiece by remember { mutableIntStateOf(PieceCode.WQ) }
  var isFlipped by remember { mutableStateOf(false) }

  val validationError = remember(editorState) { MoveFeedbackAnalyzer.validateCustomPosition(editorState) }

  val palette = remember {
    listOf(
      PieceCode.EMPTY to "Erase",
      PieceCode.WK to "♔",
      PieceCode.WQ to "♕",
      PieceCode.WR to "♖",
      PieceCode.WB to "♗",
      PieceCode.WN to "♘",
      PieceCode.WP to "♙",
      PieceCode.BK to "♚",
      PieceCode.BQ to "♛",
      PieceCode.BR to "♜",
      PieceCode.BB to "♝",
      PieceCode.BN to "♞",
      PieceCode.BP to "♟",
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("board_editor_tab"),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      ChessBoardView(
        boardState = editorState,
        selectedSquare = null,
        legalMoves = emptyList(),
        lastMoveFrom = null,
        lastMoveTo = null,
        isFlipped = isFlipped,
        boardTheme = BoardThemeOption.fromKey(settings.boardTheme),
        pieceStyle = PieceStyleOption.fromKey(settings.pieceStyle),
        showCoordinates = settings.showCoordinates,
        showLegalMoves = false,
        animateMoves = false,
        onSquareTap = { sq ->
          val nextBoard = editorState.board.copyOf()
          nextBoard[sq] = if (nextBoard[sq] == selectedBrushPiece && selectedBrushPiece != PieceCode.EMPTY) {
            PieceCode.EMPTY
          } else {
            selectedBrushPiece
          }
          val hash = ChessRules.computeHash(nextBoard, editorState.turn, editorState.castlingMask, -1)
          editorState = editorState.copy(board = nextBoard, enPassantSq = -1, zobristHash = hash)
        },
        modifier = Modifier.widthIn(max = 440.dp),
      )
    }

    item {
      Card(modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Piece Palette (Tap piece, then tap board)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            palette.forEach { (code, symbol) ->
              FilterChip(
                selected = selectedBrushPiece == code,
                onClick = { selectedBrushPiece = code },
                label = { Text(symbol) },
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Turn:", style = MaterialTheme.typography.labelMedium)
            FilterChip(
              selected = editorState.turn == PieceColor.WHITE,
              onClick = {
                val hash = ChessRules.computeHash(editorState.board, PieceColor.WHITE, editorState.castlingMask, -1)
                editorState = editorState.copy(turn = PieceColor.WHITE, zobristHash = hash)
              },
              label = { Text("White") },
            )
            FilterChip(
              selected = editorState.turn == PieceColor.BLACK,
              onClick = {
                val hash = ChessRules.computeHash(editorState.board, PieceColor.BLACK, editorState.castlingMask, -1)
                editorState = editorState.copy(turn = PieceColor.BLACK, zobristHash = hash)
              },
              label = { Text("Black") },
            )
            FilterChip(
              selected = isFlipped,
              onClick = { isFlipped = !isFlipped },
              label = { Text("Flip") },
            )
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val rights = listOf(
              CastlingMask.WHITE_KING to "K",
              CastlingMask.WHITE_QUEEN to "Q",
              CastlingMask.BLACK_KING to "k",
              CastlingMask.BLACK_QUEEN to "q",
            )
            rights.forEach { (mask, label) ->
              val active = (editorState.castlingMask and mask) != 0
              FilterChip(
                selected = active,
                onClick = {
                  val newMask = if (active) editorState.castlingMask and mask.inv() else editorState.castlingMask or mask
                  val hash = ChessRules.computeHash(editorState.board, editorState.turn, newMask, -1)
                  editorState = editorState.copy(castlingMask = newMask, zobristHash = hash)
                },
                label = { Text(label) },
              )
            }
            OutlinedButton(onClick = { editorState = ChessRules.initialBoardState() }) {
              Text("Reset")
            }
            OutlinedButton(
              onClick = {
                val empty = IntArray(64)
                empty[4] = PieceCode.WK
                empty[60] = PieceCode.BK
                val hash = ChessRules.computeHash(empty, PieceColor.WHITE, 0, -1)
                editorState = BoardState(empty, PieceColor.WHITE, 0, -1, 0, 1, hash)
              },
            ) {
              Text("Kings Only")
            }
          }

          if (validationError != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
              Text(
                text = validationError,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp),
              )
            }
          }

          OutlinedTextField(
            value = fenInput,
            onValueChange = { fenInput = it },
            label = { Text("FEN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = {
                val parsed = ChessNotation.fromFen(fenInput)
                if (parsed != null) {
                  editorState = parsed
                  onMessage("FEN loaded to editor")
                } else {
                  onMessage("Invalid FEN format")
                }
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Apply FEN")
            }
            OutlinedButton(
              onClick = {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cb?.setPrimaryClip(ClipData.newPlainText("ChessHub FEN", fenInput))
                onMessage("FEN copied to clipboard")
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Copy FEN")
            }
          }

          Spacer(modifier = Modifier.height(2.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = { onAnalyzeFen(fenInput) },
              enabled = validationError == null,
              modifier = Modifier.weight(1f),
            ) {
              Text("Analyze Position")
            }
            Button(
              onClick = { onPlayFenVsBot(fenInput, BotCatalog.presets[4]) },
              enabled = validationError == null,
              modifier = Modifier.weight(1f),
            ) {
              Text("Play vs Bot")
            }
          }
        }
      }
    }
  }
}
