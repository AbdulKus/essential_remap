package com.abdulkus.essentialremap

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdulkus.essentialremap.platform.AccessibilityStatusReader
import com.abdulkus.essentialremap.setup.PackageOperation
import com.abdulkus.essentialremap.ui.AppLanguage
import com.abdulkus.essentialremap.ui.EssentialRemapApp
import com.abdulkus.essentialremap.ui.EssentialRemapTheme
import com.abdulkus.essentialremap.ui.MapperViewModel
import com.abdulkus.essentialremap.ui.UserPreferences
import com.abdulkus.essentialremap.ui.translate
import com.abdulkus.essentialremap.update.DownloadedUpdate
import com.abdulkus.essentialremap.update.GitHubRelease
import com.abdulkus.essentialremap.update.GitHubUpdateManager
import com.abdulkus.essentialremap.update.InAppPromptHost
import com.abdulkus.essentialremap.update.UpdatePolicy
import com.abdulkus.essentialremap.update.UpdatePromptState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as EssentialKeyApplication).container
    private val viewModel: MapperViewModel by viewModels {
        MapperViewModel.Factory(
            container.repository,
            container.hapticEngine,
            container.setupCoordinator,
            container.launchableAppsReader,
        )
    }
    private val accessibilityStatusReader by lazy { AccessibilityStatusReader(this) }
    private val userPreferences by lazy { UserPreferences(this) }
    private val updateManager by lazy { GitHubUpdateManager(this) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updatePromptState = MutableStateFlow<UpdatePromptState>(UpdatePromptState.None)
    private val showSupportPrompt = MutableStateFlow(false)
    private var pendingPackageOperation: PackageOperation? = null
    private var pendingInstallFile: File? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val operation = pendingPackageOperation.also { pendingPackageOperation = null }
        if (operation != null) {
            if (!granted) {
                val language = userPreferences.language ?: AppLanguage.ENGLISH
                Toast.makeText(
                    this,
                    language.translate(
                        "Without notifications, enter a pairing code after returning to the app",
                        "Без уведомлений код сопряжения придётся вводить после возврата в приложение",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
            startWirelessSetup(operation)
        }
    }

    private val unknownSourcesPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val file = pendingInstallFile
        if (file != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            launchPackageInstaller(file)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initiallyOpenSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        val normalConfiguredLaunch = savedInstanceState == null &&
            !initiallyOpenSettings &&
            userPreferences.onboardingComplete
        if (normalConfiguredLaunch) {
            val launchCount = userPreferences.recordConfiguredLaunch()
            showSupportPrompt.value = UpdatePolicy.shouldShowSupportPrompt(launchCount)
        }

        setContent {
            val updateState = updatePromptState.collectAsStateWithLifecycle().value
            val supportVisible = showSupportPrompt.collectAsStateWithLifecycle().value
            EssentialRemapTheme {
                Box(Modifier.fillMaxSize()) {
                    EssentialRemapApp(
                        viewModel = viewModel,
                        preferences = userPreferences,
                        initiallyOpenSettings = initiallyOpenSettings,
                        openAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        openNotificationPolicySettings = ::openNotificationPolicySettings,
                        openDeveloperOptions = {
                            startActivity(developerOptionsIntent())
                        },
                        openAssistantSettings = ::openAssistantSettings,
                        openAppInfo = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:$packageName")
                                },
                            )
                        },
                        openDonate = ::openDonate,
                        checkForUpdates = { startUpdateCheck(showResult = true) },
                        openSetupVideo = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")))
                        },
                        beginPackageSetup = ::beginPackageSetup,
                        copyText = ::copyToClipboard,
                    )
                    InAppPromptHost(
                        language = userPreferences.language ?: AppLanguage.ENGLISH,
                        updateState = updateState,
                        showSupportPrompt = supportVisible,
                        onDownloadUpdate = ::downloadUpdate,
                        onInstallUpdate = ::installUpdate,
                        onDismissUpdate = { updatePromptState.value = UpdatePromptState.Dismissed },
                        onDonate = {
                            showSupportPrompt.value = false
                            openDonate()
                        },
                        onDismissSupport = { showSupportPrompt.value = false },
                    )
                }
            }
        }

        if (normalConfiguredLaunch) startUpdateCheck()
    }

    override fun onResume() {
        super.onResume()
        refreshRuntimeState()
        // Accessibility settings can report the old enabled-service list for a short moment
        // after returning to the app. Re-read it twice so the home status clears immediately.
        activityScope.launch {
            delay(350)
            refreshRuntimeState()
            delay(850)
            refreshRuntimeState()
        }
    }

    private fun refreshRuntimeState() {
        viewModel.refreshSetup()
        viewModel.updateAccessibilityStatus(accessibilityStatusReader.read())
        val notificationManager = getSystemService(NotificationManager::class.java)
        viewModel.updateNotificationPolicyAccess(notificationManager.isNotificationPolicyAccessGranted)
        viewModel.updateDeveloperOptionsStatus(
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun startUpdateCheck(showResult: Boolean = false) {
        updatePromptState.value = UpdatePromptState.Checking
        activityScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { release ->
                    updatePromptState.value = release?.let { UpdatePromptState.Available(it) }
                        ?: UpdatePromptState.None
                    if (showResult && release == null) {
                        val language = userPreferences.language ?: AppLanguage.ENGLISH
                        Toast.makeText(
                            this@MainActivity,
                            language.translate("Latest version installed", "Установлена последняя версия"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .onFailure {
                    updatePromptState.value = UpdatePromptState.None
                    if (showResult) {
                        val language = userPreferences.language ?: AppLanguage.ENGLISH
                        Toast.makeText(
                            this@MainActivity,
                            language.translate("Could not check for updates", "Не удалось проверить обновления"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
        }
    }

    private fun downloadUpdate(release: GitHubRelease) {
        updatePromptState.value = UpdatePromptState.Downloading(release, 0)
        activityScope.launch {
            runCatching {
                updateManager.download(release) { progress ->
                    updatePromptState.value = UpdatePromptState.Downloading(release, progress)
                }
            }.onSuccess { downloaded ->
                updatePromptState.value = UpdatePromptState.Ready(downloaded)
            }.onFailure { error ->
                updatePromptState.value = UpdatePromptState.Error(
                    release,
                    error.message?.take(160) ?: "Unknown error",
                )
            }
        }
    }

    private fun installUpdate(update: DownloadedUpdate) {
        val file = update.file
        if (!file.isFile) {
            updatePromptState.value = UpdatePromptState.Error(update.release, "Downloaded APK is missing")
            return
        }
        pendingInstallFile = file
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            unknownSourcesPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        launchPackageInstaller(file)
    }

    private fun launchPackageInstaller(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure {
            val language = userPreferences.language ?: AppLanguage.ENGLISH
            val text = language.translate(
                "Could not open the Android package installer",
                "Не удалось открыть установщик Android",
            )
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun beginPackageSetup(operation: PackageOperation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPackageOperation = operation
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startWirelessSetup(operation)
    }

    private fun startWirelessSetup(operation: PackageOperation) {
        // The coordinator first tries the persisted ADB identity. It opens Wireless debugging
        // settings only when the service is unavailable or pairing is actually required.
        viewModel.startPackageSetup(operation)
    }

    private fun developerOptionsIntent() =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(
            SETTINGS_FRAGMENT_ARGUMENT_KEY,
            WIRELESS_DEBUGGING_PREFERENCE_KEY,
        )

    private fun openAssistantSettings() {
        val voiceSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        val intent = if (voiceSettings.resolveActivity(packageManager) != null) {
            voiceSettings
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        startActivity(intent)
    }

    private fun openNotificationPolicySettings() {
        val detailIntent = Intent(
            ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS,
            Uri.parse("package:$packageName"),
        ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (detailIntent.resolveActivity(packageManager) != null &&
            runCatching { startActivity(detailIntent) }.isSuccess
        ) {
            return
        }
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun openDonate() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://abdulkus.github.io/donate")))
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("Essential Remap", text),
        )
        val language = userPreferences.language ?: AppLanguage.ENGLISH
        val message = language.translate("Copied", "Скопировано")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        private const val ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS =
            "android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS"
        private const val SETTINGS_FRAGMENT_ARGUMENT_KEY = ":settings:fragment_args_key"
        private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"
    }
}
