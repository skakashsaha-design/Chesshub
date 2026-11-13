package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.database.GameSummaryRow
import java.text.DateFormat
import java.util.Date

@Composable
fun GameHistoryDialog(
  games: List<GameSummaryRow>,
  query: String,
  sortBy: String,
  onQueryChange: (String) -> Unit,
  onSortChange: (String) -> Unit,
  onLoadMore: () -> Unit,
  onReplay: (Long) -> Unit,
  onDelete: (Long) -> Unit,
  onImportPgn: (String) -> Boolean,
  onDismiss: () -> Unit,
) {
  var showImportDialog by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.history_title))
        IconButton(onClick = { showImportDialog = true }) {
          Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.history_import_pgn))
        }
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = query,
          onValueChange = onQueryChange,
          placeholder = { Text(stringResource(R.string.history_search_hint)) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = sortBy == "NEWEST",
            onClick = { onSortChange("NEWEST") },
            label = { Text(stringResource(R.string.history_sort_newest)) },
          )
          FilterChip(
            selected = sortBy == "OLDEST",
            onClick = { onSortChange("OLDEST") },
            label = { Text(stringResource(R.string.history_sort_oldest)) },
          )
          FilterChip(
            selected = sortBy == "LONGEST",
            onClick = { onSortChange("LONGEST") },
            label = { Text(stringResource(R.string.history_sort_longest)) },
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (games.isEmpty()) {
          Text(
            text = stringResource(R.string.home_no_recent_games),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(games, key = { it.id }) { g ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onReplay(g.id)
                    onDismiss()
                  },
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${g.whiteName} vs ${g.blackName}",
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val dateStr = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(g.dateMillis))
                    Text(
                      text = "${g.result} • ${g.timeControlLabel} • ${g.movesCount}m • $dateStr",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!g.openingName.isNullOrBlank()) {
                      Text(
                        text = "${g.ecoCode.orEmpty()} ${g.openingName}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(4.dp))
                  IconButton(onClick = { onDelete(g.id) }) {
                    Icon(
                      Icons.Filled.Delete,
                      contentDescription = stringResource(R.string.history_delete),
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
            if (games.size >= 40) {
              item {
                OutlinedButton(
                  onClick = onLoadMore,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text("Load More")
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )

  if (showImportDialog) {
    PgnImportDialog(
      onImport = { pgn ->
        if (onImportPgn(pgn)) {
          showImportDialog = false
        }
      },
      onDismiss = { showImportDialog = false },
    )
  }
}

@Composable
fun PgnImportDialog(
  onImport: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.history_import_pgn)) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.history_pgn_paste_hint)) },
        minLines = 5,
        maxLines = 9,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      Button(onClick = { onImport(text) }) {
        Text(stringResource(R.string.history_import_pgn))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
