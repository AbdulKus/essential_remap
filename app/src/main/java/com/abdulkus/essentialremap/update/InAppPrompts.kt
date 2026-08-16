package com.abdulkus.essentialremap.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.translate

@Composable
fun InAppPromptHost(
    language: AppLanguage,
    updateState: UpdatePromptState,
    showSupportPrompt: Boolean,
    onDownloadUpdate: (GitHubRelease) -> Unit,
    onInstallUpdate: (DownloadedUpdate) -> Unit,
    onDismissUpdate: () -> Unit,
    onDonate: () -> Unit,
    onDismissSupport: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (updateState) {
            UpdatePromptState.Checking -> Unit
            UpdatePromptState.None -> if (showSupportPrompt) {
                PromptSurface {
                    Text(
                        language.t("Support Essential Remap", "Поддержи разработчика"),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        language.t(
                            "The app stays free and open-source. If it is useful, you can support further development.",
                            "Приложение остаётся бесплатным и open-source. Если оно полезно, можно поддержать дальнейшую разработку.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PromptButtons(
                        language.t("NOT NOW", "НЕ СЕЙЧАС"),
                        language.t("SUPPORT", "ПОДДЕРЖАТЬ"),
                        onDismissSupport,
                        onDonate,
                    )
                }
            }
            UpdatePromptState.Dismissed -> Unit
            is UpdatePromptState.Available -> {
                val release = updateState.release
                PromptSurface {
                    Text(
                        language.t(
                            "Update ${release.version} is available",
                            "Доступно обновление ${release.version}",
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildString {
                            append(release.title)
                            if (release.apkSizeBytes > 0L) append(" · ${formatMegabytes(release.apkSizeBytes)}")
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PromptButtons(
                        language.t("LATER", "ПОЗЖЕ"),
                        language.t("DOWNLOAD", "СКАЧАТЬ"),
                        onDismissUpdate,
                    ) { onDownloadUpdate(release) }
                }
            }
            is UpdatePromptState.Downloading -> {
                PromptSurface {
                    Text(language.t("Downloading update", "Скачивание обновления"), fontWeight = FontWeight.Bold)
                    val percent = updateState.progressPercent
                    Text(
                        percent?.let { "$it%" } ?: language.t("Downloading…", "Скачивание…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (percent != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
            is UpdatePromptState.Ready -> {
                PromptSurface {
                    Text(language.t("Update downloaded", "Обновление скачано"), fontWeight = FontWeight.Bold)
                    Text(
                        language.t(
                            "Android will ask you to confirm the installation.",
                            "Android попросит подтвердить установку.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PromptButtons(
                        language.t("LATER", "ПОЗЖЕ"),
                        language.t("INSTALL", "УСТАНОВИТЬ"),
                        onDismissUpdate,
                    ) { onInstallUpdate(updateState.update) }
                }
            }
            is UpdatePromptState.Error -> {
                PromptSurface {
                    Text(language.t("Download failed", "Не удалось скачать обновление"), fontWeight = FontWeight.Bold)
                    Text(
                        language.t(
                            "Check your connection and try again. The APK is also verified before installation.",
                            "Проверьте интернет и попробуйте снова. Перед установкой APK также проверяется.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PromptButtons(
                        language.t("CLOSE", "ЗАКРЫТЬ"),
                        language.t("RETRY", "ПОВТОРИТЬ"),
                        onDismissUpdate,
                    ) { onDownloadUpdate(updateState.release) }
                }
            }
        }
    }
}

@Composable
private fun PromptSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PromptButtons(
    secondaryText: String,
    primaryText: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSecondary) { Text(secondaryText) }
        Spacer(Modifier.width(6.dp))
        Button(onClick = onPrimary) { Text(primaryText) }
    }
}

private fun AppLanguage.t(en: String, ru: String): String = translate(en, ru)

private fun formatMegabytes(bytes: Long): String =
    String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
