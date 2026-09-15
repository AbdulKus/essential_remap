package com.abdulkus.essentialremap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import com.abdulkus.essentialremap.platform.LaunchableActivity
import com.abdulkus.essentialremap.platform.LaunchableApp
import com.abdulkus.essentialremap.platform.LaunchableAppsReader

@Composable
fun AppActivityPickerDialog(
    language: AppLanguage,
    apps: List<LaunchableApp>,
    dismiss: () -> Unit,
    select: (LaunchableActivity) -> Unit,
) {
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val reader = remember(context) { LaunchableAppsReader(context) }
    val app = selectedApp
    val activities = remember(app?.packageName) {
        app?.let { reader.readActivities(it.packageName) }.orEmpty()
    }
    val filteredApps = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
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
                        language.translate("Launch Activity", "Запуск Activity"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = dismiss) {
                        Text(language.translate("CLOSE", "ЗАКРЫТЬ"))
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = {
                        Text(
                            if (app == null) {
                                language.translate("Search app", "Поиск приложения")
                            } else {
                                language.translate("Search Activity", "Поиск Activity")
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
                            ActivityRow(activity) { select(activity) }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = click)
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = click)
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
