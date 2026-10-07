package com.claudeusage.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.service.UsageNotificationService
import com.claudeusage.widget.service.UsageUpdateScheduler
import com.claudeusage.widget.ui.screens.CodexUiState
import com.claudeusage.widget.ui.screens.ForecastScreen
import com.claudeusage.widget.ui.screens.MetricToggle
import com.claudeusage.widget.ui.screens.SettingsScreen
import com.claudeusage.widget.ui.screens.PrivacyPolicyScreen
import com.claudeusage.widget.ui.screens.UiState
import com.claudeusage.widget.ui.screens.UsageScreen
import com.claudeusage.widget.ui.screens.UsageViewModel
import com.claudeusage.widget.ui.components.InterstitialAdManager
import com.claudeusage.widget.ui.theme.ClaudeUsageTheme
import com.claudeusage.widget.widget.UsageWidgetReceiver

private enum class Screen { Usage, Settings, Forecast, PrivacyPolicy }

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()
    private lateinit var appPreferences: AppPreferences

    private var currentScreen by mutableStateOf(Screen.Usage)
    private var notificationEnabled by mutableStateOf(false)
    private var coachEnabled by mutableStateOf(true)
    private var themeMode by mutableStateOf(AppPreferences.THEME_DARK)
    private var primaryMode by mutableStateOf(AppPreferences.MODE_CLAUDE)
    private var hiddenMetrics by mutableStateOf<Set<String>>(emptySet())
    private var hiddenGraphSeries by mutableStateOf<Set<String>>(emptySet())
    private var showAccountHint by mutableStateOf(false)
    private var showNotificationPrompt by mutableStateOf(false)
    private val interstitialAdManager = InterstitialAdManager()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val sessionKey = result.data?.getStringExtra(LoginActivity.EXTRA_SESSION_KEY)
            if (sessionKey != null) {
                viewModel.onLoginComplete(sessionKey)
            }
        }
    }

    private val codexLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra(CodexLoginActivity.EXTRA_COOKIES)
            if (cookies != null) {
                viewModel.onCodexLoginComplete(cookies)
            }
        }
    }

    /**
     * Permission asked for from the post-login prompt. Granting it only lets
     * alerts through; it must not switch on the persistent notification,
     * which stays an explicit opt-in in Settings.
     */
    private val alertPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    /** Permission asked for by the Persistent Notification toggle itself. */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            appPreferences.notificationEnabled = true
            notificationEnabled = true
            if (viewModel.uiState.value is UiState.Success) {
                UsageNotificationService.start(applicationContext)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(applicationContext)
        notificationEnabled = appPreferences.notificationEnabled
        coachEnabled = appPreferences.coachEnabled
        themeMode = appPreferences.themeMode
        primaryMode = appPreferences.primaryMode

        // Load metric visibility from preferences
        hiddenMetrics = appPreferences.hiddenMetricKeys
        hiddenGraphSeries = appPreferences.hiddenGraphSeries
        showAccountHint = !appPreferences.accountHintDismissed

        // Preload interstitial ad
        interstitialAdManager.load(this)

        // Schedule background updates
        UsageUpdateScheduler.schedule(applicationContext)

        setContent {
            val isDark = when (themeMode) {
                AppPreferences.THEME_LIGHT -> false
                AppPreferences.THEME_SYSTEM -> isSystemInDarkTheme()
                else -> true
            }
            ClaudeUsageTheme(darkTheme = isDark) {
                val uiState by viewModel.uiState.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val lastUpdated by viewModel.lastUpdated.collectAsState()
                val usageHistory by viewModel.usageHistory.collectAsState()
                val codexState by viewModel.codexState.collectAsState()
                val claudeAccounts by viewModel.claudeAccounts.collectAsState()
                val codexAccounts by viewModel.codexAccounts.collectAsState()

                // Ask for notifications once, after the first successful load, with
                // context, instead of a bare system dialog before the user has signed in
                val isLoaded = uiState is UiState.Success
                LaunchedEffect(isLoaded) {
                    if (isLoaded && shouldAskForNotifications()) showNotificationPrompt = true
                }
                if (showNotificationPrompt) {
                    AlertDialog(
                        onDismissRequest = ::answerNotificationPrompt,
                        title = { Text("Allow notifications?") },
                        text = {
                            Text(
                                "Claude Meter can alert you about your usage limits " +
                                    "(Productivity Coach). You can also show usage in the " +
                                    "status bar from Settings."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                answerNotificationPrompt()
                                alertPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("Allow") }
                        },
                        dismissButton = {
                            TextButton(onClick = ::answerNotificationPrompt) { Text("Not now") }
                        }
                    )
                }

                when (currentScreen) {
                    Screen.Usage -> {
                        UsageScreen(
                            uiState = uiState,
                            isRefreshing = isRefreshing,
                            lastUpdated = lastUpdated,
                            hiddenMetrics = hiddenMetrics,
                            codexState = codexState,
                            primaryMode = primaryMode,
                            onModeChange = { mode -> applyPrimaryMode(mode) },
                            onRefresh = viewModel::refresh,
                            onLogout = {
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.logout()
                                    cancelUpdatesIfNoClaudeAccount()
                                }
                            },
                            onLoginClick = { launchLogin() },
                            onSettingsClick = { currentScreen = Screen.Settings },
                            onForecastClick = { currentScreen = Screen.Forecast },
                            onCodexLoginClick = { launchCodexLogin() },
                            onCodexLogout = {
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.logoutCodex()
                                }
                            },
                            claudeAccounts = claudeAccounts,
                            onSwitchAccount = viewModel::switchClaudeAccount,
                            codexAccounts = codexAccounts,
                            onSwitchCodexAccount = viewModel::switchCodexAccount,
                            showAccountHint = showAccountHint,
                            onDismissAccountHint = ::dismissAccountHint
                        )
                    }
                    Screen.Settings -> {
                        BackHandler { currentScreen = Screen.Usage }
                        // Only what the current mode can actually hide. The
                        // primary provider is the screen, so it has no toggle;
                        // the Claude per-model metrics only render in Claude mode.
                        val usageData = (uiState as? UiState.Success)?.data
                        val availableToggles = buildList {
                            if (primaryMode == AppPreferences.MODE_CHATGPT) {
                                val claudeKey = AppPreferences.CLAUDE_METRIC_KEY
                                add(
                                    MetricToggle(
                                        claudeKey,
                                        "Claude Usage",
                                        claudeKey !in hiddenMetrics
                                    )
                                )
                            } else {
                                usageData?.extraMetrics?.forEach { labeled ->
                                    add(
                                        MetricToggle(
                                            labeled.key,
                                            labeled.label,
                                            labeled.key !in hiddenMetrics
                                        )
                                    )
                                }
                                val codexKey = AppPreferences.CODEX_METRIC_KEY
                                add(
                                    MetricToggle(
                                        codexKey,
                                        "Codex Usage",
                                        codexKey !in hiddenMetrics
                                    )
                                )
                            }
                        }

                        SettingsScreen(
                            notificationEnabled = notificationEnabled,
                            onNotificationToggle = { enabled ->
                                handleNotificationToggle(enabled)
                            },
                            coachEnabled = coachEnabled,
                            onCoachToggle = { enabled ->
                                coachEnabled = enabled
                                appPreferences.coachEnabled = enabled
                            },
                            metricToggles = availableToggles,
                            onMetricToggle = { key, enabled ->
                                handleMetricToggle(key, enabled)
                            },
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                appPreferences.themeMode = mode
                            },
                            primaryMode = primaryMode,
                            onPrimaryModeChange = { mode -> applyPrimaryMode(mode) },
                            onPrivacyPolicyClick = { currentScreen = Screen.PrivacyPolicy },
                            claudeAccounts = claudeAccounts,
                            onSwitchClaudeAccount = viewModel::switchClaudeAccount,
                            onAddClaudeAccount = { launchLogin() },
                            onRemoveClaudeAccount = { id ->
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.removeClaudeAccount(id)
                                    if (cancelUpdatesIfNoClaudeAccount()) {
                                        // Nothing left to show here; the usage screen offers login
                                        currentScreen = Screen.Usage
                                    }
                                }
                            },
                            onRenameClaudeAccount = viewModel::renameClaudeAccount,
                            codexAccounts = codexAccounts,
                            onSwitchCodexAccount = viewModel::switchCodexAccount,
                            onRenameCodexAccount = viewModel::renameCodexAccount,
                            onAddCodexAccount = { launchCodexLogin() },
                            onRemoveCodexAccount = { id ->
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.removeCodexAccount(id)
                                }
                            },
                            onBack = { currentScreen = Screen.Usage }
                        )
                    }
                    Screen.PrivacyPolicy -> {
                        BackHandler { currentScreen = Screen.Settings }
                        PrivacyPolicyScreen(
                            onBack = { currentScreen = Screen.Settings }
                        )
                    }
                    Screen.Forecast -> {
                        BackHandler { currentScreen = Screen.Usage }
                        val usageData = (uiState as? UiState.Success)?.data
                        val codexData = (codexState as? CodexUiState.Connected)?.data
                            ?: (uiState as? UiState.Success)?.codexData
                        ForecastScreen(
                            usageData = usageData,
                            codexData = codexData,
                            history = usageHistory,
                            hiddenMetrics = hiddenMetrics,
                            hiddenSeries = hiddenGraphSeries,
                            onToggleSeries = { key, visible ->
                                val updated = if (visible) {
                                    hiddenGraphSeries - key
                                } else {
                                    hiddenGraphSeries + key
                                }
                                hiddenGraphSeries = updated
                                appPreferences.hiddenGraphSeries = updated
                            },
                            onBack = { currentScreen = Screen.Usage }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
        val hasData = viewModel.uiState.value is UiState.Success ||
            viewModel.codexState.value is CodexUiState.Connected
        if (hasData) {
            viewModel.refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackground()
    }

    /** Stops background updates once no Claude account is left; returns true if it did. */
    private fun cancelUpdatesIfNoClaudeAccount(): Boolean {
        if (viewModel.claudeAccounts.value.accounts.isNotEmpty()) return false
        UsageUpdateScheduler.cancel(applicationContext)
        return true
    }

    private fun shouldAskForNotifications(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !appPreferences.notificationPromptShown &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun answerNotificationPrompt() {
        showNotificationPrompt = false
        appPreferences.notificationPromptShown = true
    }

    private fun dismissAccountHint() {
        if (!showAccountHint) return
        showAccountHint = false
        appPreferences.accountHintDismissed = true
    }

    private fun launchLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        loginLauncher.launch(intent)
    }

    private fun launchCodexLogin() {
        val intent = Intent(this, CodexLoginActivity::class.java)
        codexLoginLauncher.launch(intent)
    }

    private fun handleNotificationToggle(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    appPreferences.notificationEnabled = true
                    notificationEnabled = true
                    if (viewModel.uiState.value is UiState.Success) {
                        UsageNotificationService.start(applicationContext)
                    }
                } else {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            } else {
                appPreferences.notificationEnabled = true
                notificationEnabled = true
                if (viewModel.uiState.value is UiState.Success) {
                    UsageNotificationService.start(applicationContext)
                }
            }
        } else {
            appPreferences.notificationEnabled = false
            notificationEnabled = false
            UsageNotificationService.stop(applicationContext)
        }
    }

    /** Switches which provider the app is centered on and refreshes its surfaces. */
    private fun applyPrimaryMode(mode: String) {
        if (mode == primaryMode) return
        primaryMode = mode
        appPreferences.primaryMode = mode
        // The primary provider is the screen, so it must not stay hidden: a key
        // switched off in the other mode would otherwise keep it off the
        // forecast graph with no toggle left to switch it back on.
        val primaryKey = if (mode == AppPreferences.MODE_CHATGPT) {
            AppPreferences.CODEX_METRIC_KEY
        } else {
            AppPreferences.CLAUDE_METRIC_KEY
        }
        if (primaryKey in hiddenMetrics) {
            hiddenMetrics = hiddenMetrics - primaryKey
            appPreferences.hiddenMetricKeys = hiddenMetrics
        }
        // Widget and notification follow the primary provider
        try {
            UsageWidgetReceiver.updateWidget(applicationContext)
        } catch (_: Exception) {
            // Widget might not be placed
        }
        if (appPreferences.notificationEnabled) {
            UsageNotificationService.forceUpdate(applicationContext)
        }
        if (mode == AppPreferences.MODE_CHATGPT) {
            viewModel.refreshCodex()
        }
    }

    private fun handleMetricToggle(key: String, enabled: Boolean) {
        hiddenMetrics = if (enabled) hiddenMetrics - key else hiddenMetrics + key
        appPreferences.hiddenMetricKeys = hiddenMetrics
    }
}
