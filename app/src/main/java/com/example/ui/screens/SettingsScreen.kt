package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppSection
import com.example.R
import com.example.chess.PieceCode
import com.example.database.AppSettingsEntity
import com.example.engine.MemoryProfile
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.PieceStyleOption
import com.example.ui.board.PieceVectorRenderer
import com.example.ui.theme.AppAccentOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: AppSettingsEntity,
  memoryProfile: MemoryProfile,
  onUpdateSettings: ((AppSettingsEntity) -> AppSettingsEntity) -> Unit,
  onLanguageChanged: (String) -> Unit,
  onImportPgn: (String) -> Boolean,
  onExportAllPgn: suspend () -> String,
  onSharePgn: (String) -> Unit,
  onClearCache: () -> Unit,
  onDeleteAllData: () -> Unit,
  onNavigate: (AppSection) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var showImportDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var infoDialogTitle by remember { mutableStateOf<String?>(null) }
  var infoDialogBody by remember { mutableStateOf<String?>(null) }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("settings_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
      )
    }

    // Appearance
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            "LIGHT" to stringResource(R.string.settings_theme_light),
            "DARK" to stringResource(R.string.settings_theme_dark),
            "SYSTEM" to stringResource(R.string.settings_theme_system),
          ).forEach { (key, label) ->
            FilterChip(
              selected = settings.themeMode == key,
              onClick = { onUpdateSettings { it.copy(themeMode = key) } },
              label = { Text(label) },
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.settings_accent_color), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AppAccentOption.entries.forEach { accent ->
            FilterChip(
              selected = settings.appAccent == accent.key,
              onClick = { onUpdateSettings { it.copy(appAccent = accent.key) } },
              label = { Text(accent.displayName) },
            )
          }
        }
      }
    }

    // Chess
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_chess)) {
        val activeBoard = remember(settings.boardTheme) { BoardThemeOption.fromKey(settings.boardTheme) }
        val activeStyle = remember(settings.pieceStyle) { PieceStyleOption.fromKey(settings.pieceStyle) }
        val sampleRow = remember {
          intArrayOf(
            PieceCode.WK, PieceCode.WQ, PieceCode.WR, PieceCode.WB,
            PieceCode.WN, PieceCode.WP, PieceCode.BQ, PieceCode.BK,
          )
        }
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MaterialTheme.shapes.small),
        ) {
          val sqW = size.width / sampleRow.size
          val sqH = size.height
          val pieceSize = minOf(sqW, sqH)
          sampleRow.forEachIndexed { idx, code ->
            val bg = if (idx % 2 == 0) activeBoard.lightSquare else activeBoard.darkSquare
            drawRect(color = bg, topLeft = Offset(idx * sqW, 0f), size = Size(sqW, sqH))
            with(PieceVectorRenderer) {
              drawPiece(
                pieceCode = code,
                topLeft = Offset(idx * sqW + (sqW - pieceSize) / 2f, (sqH - pieceSize) / 2f),
                squareSize = pieceSize,
                style = activeStyle,
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.settings_board_theme), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          BoardThemeOption.entries.forEach { theme ->
            FilterChip(
              selected = settings.boardTheme == theme.key,
              onClick = { onUpdateSettings { it.copy(boardTheme = theme.key) } },
              label = { Text(theme.displayName) },
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.settings_piece_style), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PieceStyleOption.entries.forEach { style ->
            FilterChip(
              selected = settings.pieceStyle == style.key,
              onClick = { onUpdateSettings { it.copy(pieceStyle = style.key) } },
              label = { Text(style.displayName) },
            )
          }
        }
        SettingsToggleRow(stringResource(R.string.settings_coordinates), settings.showCoordinates) { v ->
          onUpdateSettings { it.copy(showCoordinates = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_animations), settings.animateMoves) { v ->
          onUpdateSettings { it.copy(animateMoves = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_move_confirm), settings.confirmMoves) { v ->
          onUpdateSettings { it.copy(confirmMoves = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_legal_moves), settings.showLegalMoves) { v ->
          onUpdateSettings { it.copy(showLegalMoves = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_haptics), settings.hapticFeedback) { v ->
          onUpdateSettings { it.copy(hapticFeedback = v) }
        }
      }
    }

    // Sound
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_sound)) {
        SettingsToggleRow(stringResource(R.string.settings_sound_move), settings.soundMove) { v ->
          onUpdateSettings { it.copy(soundMove = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_sound_capture), settings.soundCapture) { v ->
          onUpdateSettings { it.copy(soundCapture = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_sound_check), settings.soundCheck) { v ->
          onUpdateSettings { it.copy(soundCheck = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_sound_end), settings.soundGameEnd) { v ->
          onUpdateSettings { it.copy(soundGameEnd = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_sound_ui), settings.soundUi) { v ->
          onUpdateSettings { it.copy(soundUi = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_sound_music), settings.musicEnabled) { v ->
          onUpdateSettings { it.copy(musicEnabled = v) }
        }
      }
    }

    // Analysis & Feedback Controls (13 Independent ON/OFF Switches)
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_analysis_controls)) {
        Text(
          text = "Toggle each feature ON or OFF independently. Disabled features hide UI and skip engine calculations.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = {
              onUpdateSettings {
                it.copy(
                  showEvalBar = true,
                  showThreatArrow = true,
                  showSuggestionArrow = true,
                  showEngineLine = true,
                  showMoveFeedback = true,
                  showBotMoveFeedback = true,
                  enableGameAnalysis = true,
                  showEvalGraph = true,
                  showCandidateMoves = true,
                  showTacticalSuggestions = true,
                  enableCriticalMoments = true,
                  showOpeningInfo = true,
                  enableEngineAnalysis = true,
                )
              }
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("All ON")
          }
          OutlinedButton(
            onClick = {
              onUpdateSettings {
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
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("All OFF")
          }
        }
        SettingsToggleRow(
          label = stringResource(R.string.toggle_engine_analysis),
          subtitle = "Master switch for live position & threat engine calculations",
          checked = settings.enableEngineAnalysis,
        ) { v -> onUpdateSettings { it.copy(enableEngineAnalysis = v) } }
        SettingsToggleRow(stringResource(R.string.toggle_eval_bar), settings.showEvalBar) { v ->
          onUpdateSettings { it.copy(showEvalBar = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_threat_arrow), settings.showThreatArrow) { v ->
          onUpdateSettings { it.copy(showThreatArrow = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_suggestion_arrow), settings.showSuggestionArrow) { v ->
          onUpdateSettings { it.copy(showSuggestionArrow = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_engine_line), settings.showEngineLine) { v ->
          onUpdateSettings { it.copy(showEngineLine = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_move_feedback), settings.showMoveFeedback) { v ->
          onUpdateSettings { it.copy(showMoveFeedback = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_bot_move_feedback), settings.showBotMoveFeedback) { v ->
          onUpdateSettings { it.copy(showBotMoveFeedback = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_game_analysis), settings.enableGameAnalysis) { v ->
          onUpdateSettings { it.copy(enableGameAnalysis = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_eval_graph), settings.showEvalGraph) { v ->
          onUpdateSettings { it.copy(showEvalGraph = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_candidate_moves), settings.showCandidateMoves) { v ->
          onUpdateSettings { it.copy(showCandidateMoves = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_tactical_suggestions), settings.showTacticalSuggestions) { v ->
          onUpdateSettings { it.copy(showTacticalSuggestions = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_critical_moments), settings.enableCriticalMoments) { v ->
          onUpdateSettings { it.copy(enableCriticalMoments = v) }
        }
        SettingsToggleRow(stringResource(R.string.toggle_opening_info), settings.showOpeningInfo) { v ->
          onUpdateSettings { it.copy(showOpeningInfo = v) }
        }
      }
    }

    // Performance & 1 GB RAM
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_performance)) {
        Text(
          text = "Device RAM: ${memoryProfile.totalRamMb} MB (Available: ${memoryProfile.availRamMb} MB)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        SettingsToggleRow(
          label = stringResource(R.string.settings_low_memory),
          subtitle = stringResource(R.string.settings_low_memory_desc),
          checked = settings.lowMemoryMode,
        ) { v ->
          onUpdateSettings { it.copy(lowMemoryMode = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_reduce_anim), settings.reduceAnimations) { v ->
          onUpdateSettings { it.copy(reduceAnimations = v) }
        }
        SettingsToggleRow(stringResource(R.string.settings_battery_saver), settings.batterySavingMode) { v ->
          onUpdateSettings { it.copy(batterySavingMode = v) }
        }
      }
    }

    // Data
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_data)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { showImportDialog = true }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_import_pgn))
          }
          OutlinedButton(
            onClick = {
              scope.launch {
                val all = onExportAllPgn()
                onSharePgn(all.ifBlank { "[Event \"ChessHub Empty Export\"]\n*" })
              }
            },
            modifier = Modifier.weight(1f),
          ) {
            Text(stringResource(R.string.settings_export_pgn))
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_clear_cache))
          }
          Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f),
          ) {
            Text(stringResource(R.string.settings_delete_data))
          }
        }
      }
    }

    // Accounts & Language & About
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_accounts)) {
        OutlinedButton(onClick = { onNavigate(AppSection.PROFILES) }, modifier = Modifier.fillMaxWidth()) {
          Text("${stringResource(R.string.home_lichess_profile)} / ${stringResource(R.string.home_chesscom_profile)}")
        }
      }
    }

    item {
      SettingsSectionCard(title = stringResource(R.string.settings_other)) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = settings.languageCode == "en",
            onClick = { onLanguageChanged("en") },
            label = { Text(stringResource(R.string.settings_lang_en)) },
            modifier = Modifier.testTag("lang_en_chip"),
          )
          FilterChip(
            selected = settings.languageCode == "bn",
            onClick = { onLanguageChanged("bn") },
            label = { Text(stringResource(R.string.settings_lang_bn)) },
            modifier = Modifier.testTag("lang_bn_chip"),
          )
        }
        val aboutTitle = stringResource(R.string.settings_about)
        val aboutDesc = "${stringResource(R.string.about_description)}\n\n${stringResource(R.string.about_disclaimer)}"
        val privacyTitle = stringResource(R.string.settings_privacy)
        val privacyDesc = stringResource(R.string.privacy_body)
        val licensesTitle = stringResource(R.string.settings_licenses)
        val licensesDesc = stringResource(R.string.licenses_body)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = {
              infoDialogTitle = aboutTitle
              infoDialogBody = aboutDesc
            },
            modifier = Modifier.weight(1f),
          ) {
            Text(aboutTitle)
          }
          OutlinedButton(
            onClick = {
              infoDialogTitle = privacyTitle
              infoDialogBody = privacyDesc
            },
            modifier = Modifier.weight(1f),
          ) {
            Text(privacyTitle)
          }
        }
        OutlinedButton(
          onClick = {
            infoDialogTitle = licensesTitle
            infoDialogBody = licensesDesc
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(licensesTitle)
        }
      }
    }
  }

  if (showImportDialog) {
    PgnImportDialog(
      onImport = { pgn ->
        if (onImportPgn(pgn)) showImportDialog = false
      },
      onDismiss = { showImportDialog = false },
    )
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.settings_delete_data)) },
      text = { Text(stringResource(R.string.settings_delete_confirm)) },
      confirmButton = {
        Button(
          onClick = {
            onDeleteAllData()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (infoDialogTitle != null && infoDialogBody != null) {
    AlertDialog(
      onDismissRequest = {
        infoDialogTitle = null
        infoDialogBody = null
      },
      title = { Text(infoDialogTitle!!) },
      text = { Text(infoDialogBody!!) },
      confirmButton = {
        TextButton(
          onClick = {
            infoDialogTitle = null
            infoDialogBody = null
          },
        ) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 600.dp),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
  label: String,
  checked: Boolean,
  subtitle: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
