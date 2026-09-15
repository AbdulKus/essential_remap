package com.abdulkus.essentialremap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.platform.LaunchableActivity
import com.abdulkus.essentialremap.platform.LaunchableApp
import com.abdulkus.essentialremap.platform.LaunchableAppsReader

@Composable
fun AppActivityActionPicker(
    viewModel: MapperViewModel,
    language: AppLanguage,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var gesture by rememberSaveable { mutableStateOf(PressAction.SINGLE) }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(end = 18.dp, bottom = 86.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        ExtendedFloatingActionButton(
            onClick = {
                selectedApp = null
                query = ""
                open = true
            },
            icon = { Icon(Icons.Default.Apps, contentDescription = null) },
            text = { Text(language.translate("APP ACTION", "ДЕЙСТВИЕ APP")) },
        )
    }

    if (!open) return

    val context = LocalContext.current
    val reader = remember(context) { LaunchableAppsReader(context) }
    val app = selectedApp
    val activities = remember(app?.packageName) {
        app?.let { reader.readActivities(it.packageName) }.orEmpty()
    }
    val filteredApps = remember(state.launchableApps, query) {
        if (query.isBlank()) state.launchableApps else state.launchableApps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    val filteredActivities = remember(activities, query) {
        if (query.isBlank()) activities else activities.filter {
            it.activityLabel.contains(query, ignoreCase = true) ||
                it.componentName.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = { open = false }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            language.translate("Direct app action", "Прямое действие приложения"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            language.translate(
                                "Starts an exported Activity directly — no shell or ADB",
                                "Запускает экспортированную Activity напрямую — без shell и ADB",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { open = false }) {
                        Text(language.translate("CLOSE", "ЗАКРЫТЬ"))
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PressAction.entries.forEach { item ->
                        val title = when (item) {
                            PressAction.SINGLE -> "1×"
                            PressAction.DOUBLE -> "2×"
                            PressAction.LONG -> language.translate("HOLD", "HOLD")
                        }
                        if (gesture == item) {
                            Button(
                                onClick = { gesture = item },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp),
                            ) { Text(title) }
                        } else {
                            OutlinedButton(
                                onClick = { gesture = item },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp),
                            ) { Text(title) }
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = {
                        Text(
                            if (app == null) language.translate("Search app", "Поиск приложения")
                            else language.translate("Search Activity", "Поиск Activity"),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                )
                if (app != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedApp = null
                                query = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (app == null) {
                        items(filteredApps, key = { it.packageName }) { candidate ->
                            AppRow(candidate) {
                                selectedApp = candidate
                                query = ""
                            }
                        }
                    } else if (activities.isEmpty()) {
                        item {
                            Text(
                                language.translate(
                                    "This app exposes no Activity that Essential Remap can start directly.",
                                    "У этого приложения нет Activity, которую Essential Remap может запустить напрямую.",
                                ),
                                modifier = Modifier.padding(20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(filteredActivities, key = { it.componentName }) { activity ->
                            ActivityRow(activity) {
                                viewModel.updateLaunchActivity(gesture, activity)
                                open = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: LaunchableApp, click: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ActivityRow(activity: LaunchableActivity, click: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(activity.activityLabel, fontWeight = FontWeight.Medium)
            Text(
                activity.componentName.substringAfter('/'),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
