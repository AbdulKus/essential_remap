package com.abdulkus.essentialremap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abdulkus.essentialremap.ScreenOffKeyAccess
import com.abdulkus.essentialremap.platform.QuickSettingsTileInfo
import com.abdulkus.essentialremap.platform.QuickSettingsTilesReader

@Composable
fun QuickSettingsTilePickerDialog(
    language: AppLanguage,
    dismiss: () -> Unit,
    select: (QuickSettingsTileInfo) -> Unit,
) {
    val context = LocalContext.current
    val tiles = remember(context) { QuickSettingsTilesReader(context).read() }
    val sleepMonitorEnabled = UserPreferences(context).screenOffEnabled
    val sleepMonitorRunning = sleepMonitorEnabled && ScreenOffKeyAccess.isGranted(context)
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(tiles, query) {
        if (query.isBlank()) tiles else tiles.filter {
            it.tileLabel.contains(query, ignoreCase = true) ||
                it.appLabel.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                it.componentName.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = dismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        language.translate("Quick Settings tile", "Плитка Quick Settings"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = dismiss) {
                        Text(language.translate("CLOSE", "ЗАКРЫТЬ"))
                    }
                }
                HorizontalDivider()
                Surface(
                    color = if (sleepMonitorRunning) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = when {
                            !sleepMonitorEnabled -> language.translate(
                                "Requires the sleep monitor. Enable it in settings before choosing a tile.",
                                "Для этого нужен монитор сна. Включите его в настройках перед выбором плитки.",
                            )
                            !sleepMonitorRunning -> language.translate(
                                "The sleep monitor is not running. Restart it before choosing a tile.",
                                "Монитор сна не запущен. Перезапустите его перед выбором плитки.",
                            )
                            else -> language.translate(
                                "Uses the running sleep monitor to trigger the exact tile directly. Quick Settings will not open.",
                                "Используется запущенный монитор сна: нужная плитка срабатывает напрямую, без открытия шторки.",
                            )
                        },
                        color = if (sleepMonitorRunning) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(language.translate("Search tile or app", "Поиск плитки или приложения")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (tiles.isEmpty()) {
                        item {
                            Text(
                                language.translate(
                                    "No app Quick Settings tiles were found.",
                                    "Плитки Quick Settings от приложений не найдены.",
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    } else {
                        items(filtered, key = { it.componentName }) { tile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = sleepMonitorRunning) { select(tile) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tile.tileLabel,
                                        fontWeight = FontWeight.Medium,
                                        color = if (sleepMonitorRunning) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    )
                                    Text(
                                        tile.appLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        tile.componentName.substringAfter('/'),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
