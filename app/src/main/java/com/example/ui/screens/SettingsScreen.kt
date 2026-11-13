package com.example.ui.screens

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
import com.example.database.AppSettingsEntity
import com.example.engine.MemoryProfile
import com.example.ui.board.BoardThemeOption
import com.example.ui.board.PieceStyleOption
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
      }
    }

    // Chess
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_chess)) {
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
