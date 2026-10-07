package com.claudeusage.widget.ui.screens

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.model.Account
import com.claudeusage.widget.data.local.CodexCredentialManager
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.local.UsageHistoryEntry
import com.claudeusage.widget.data.local.UsageHistoryStore
import com.claudeusage.widget.data.model.CodexCredentials
import com.claudeusage.widget.data.model.CodexUsageData
import com.claudeusage.widget.data.model.Credentials
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.repository.AuthException
import com.claudeusage.widget.data.repository.CodexUsageRepository
import com.claudeusage.widget.data.repository.RateLimitException
import com.claudeusage.widget.data.repository.UsageRepository
import com.claudeusage.widget.service.UsageNotificationService
import com.claudeusage.widget.widget.UsageWidgetReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

sealed class UiState {
    data object Loading : UiState()
    data object LoginRequired : UiState()
    data class Success(val data: UsageData, val codexData: CodexUsageData? = null) : UiState()
    data class Error(val message: String, val isAuthError: Boolean = false) : UiState()
}

sealed class CodexUiState {
    data object NotConnected : CodexUiState()
    data object Loading : CodexUiState()
    data class Connected(val data: CodexUsageData) : CodexUiState()
    data class Error(val message: String, val isAuthError: Boolean = false) : CodexUiState()
}

data class AccountSummary(
    val id: String,
    val label: String,
    val nickname: String = ""
)

/** Saved logins for one service and which of them is currently shown. */
data class AccountList(
    val accounts: List<AccountSummary> = emptyList(),
    val activeId: String? = null
) {
    val active: AccountSummary?
        get() = accounts.firstOrNull { it.id == activeId }

    /** Name to show: the nickname, else the email, else "Account N". */
    fun labelOf(account: AccountSummary): String =
        account.nickname.ifBlank { account.label.ifBlank { "Account ${accounts.indexOf(account) + 1}" } }

    /** The email under a nickname, so a named account stays identifiable; null otherwise. */
    fun detailOf(account: AccountSummary): String? =
        account.label.takeIf { account.nickname.isNotBlank() && it.isNotBlank() }
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialManager = CredentialManager(application)
    private val codexCredentialManager = CodexCredentialManager(application)
    private val appPreferences = AppPreferences(application)
    private val repository = UsageRepository()
    private val codexRepository = CodexUsageRepository()
    private val historyStore = UsageHistoryStore(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastUpdated = MutableStateFlow<String?>(null)
    val lastUpdated: StateFlow<String?> = _lastUpdated.asStateFlow()

    private val _usageHistory = MutableStateFlow<Map<String, List<UsageHistoryEntry>>>(emptyMap())
    val usageHistory: StateFlow<Map<String, List<UsageHistoryEntry>>> = _usageHistory.asStateFlow()

    private val _codexState = MutableStateFlow<CodexUiState>(CodexUiState.NotConnected)
    val codexState: StateFlow<CodexUiState> = _codexState.asStateFlow()

    private val _claudeAccounts = MutableStateFlow(AccountList())
    val claudeAccounts: StateFlow<AccountList> = _claudeAccounts.asStateFlow()

    private val _codexAccounts = MutableStateFlow(AccountList())
    val codexAccounts: StateFlow<AccountList> = _codexAccounts.asStateFlow()

    private var autoRefreshJob: Job? = null
    private val fetchMutex = Mutex()
    private var isAppInForeground = false
    private var prevSessionUtil: Double? = null
    private var prevWeeklyUtil: Double? = null
    private var prevDynamicUtils: Map<String, Double> = emptyMap()
    private var lastCoachEvalTime: Long = 0L

    init {
        createCoachNotificationChannel()
        refreshAccountLists()
        reloadHistory()
        checkCredentialsAndLoad()
        checkCodexCredentials()
    }

    fun checkCredentialsAndLoad() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (credentialManager.hasCredentials()) {
                fetchUsageData()
            } else {
                _uiState.value = UiState.LoginRequired
            }
        }
    }

    fun onLoginComplete(sessionKey: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val orgResult = repository.fetchOrganizationId(sessionKey)
            orgResult.fold(
                onSuccess = { orgId ->
                    val credentials = Credentials(sessionKey, orgId)
                    val label = repository.fetchAccountLabel(sessionKey).orEmpty()
                    credentialManager.saveCredentials(credentials, label)
                    onClaudeAccountChanged()
                    fetchUsageData()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to get organization info.",
                        isAuthError = error is AuthException
                    )
                }
            )
        }
    }

    fun onAppForeground() {
        isAppInForeground = true
        // ChatGPT-only users have no Claude data, but still need the refresh loop
        if (_uiState.value is UiState.Success || _codexState.value is CodexUiState.Connected) {
            startAutoRefresh()
        }
    }

    fun onAppBackground() {
        isAppInForeground = false
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun refresh() {
        viewModelScope.launch {
            if (fetchMutex.isLocked) return@launch
            _isRefreshing.value = true
            fetchUsageData()
            if (codexCredentialManager.hasCredentials()) {
                fetchCodexUsageData()
            }
            _isRefreshing.value = false
        }
    }

    /** Logs out of the active Claude account; the next saved one, if any, takes over. */
    fun logout() {
        autoRefreshJob?.cancel()
        credentialManager.activeAccountId?.let { id ->
            credentialManager.removeAccount(id)
            historyStore.clearSeriesWhere(id) { it != UsageHistoryStore.SERIES_CODEX_WEEKLY }
        }
        onClaudeAccountChanged()
        _lastUpdated.value = null
        if (credentialManager.hasCredentials()) {
            checkCredentialsAndLoad()
        } else {
            _uiState.value = UiState.LoginRequired
            UsageNotificationService.stop(getApplication())
        }
    }

    /** Removes a saved Claude account; removing the active one is the same as [logout]. */
    fun removeClaudeAccount(id: String) {
        if (id == credentialManager.activeAccountId) {
            logout()
            return
        }
        credentialManager.removeAccount(id)
        historyStore.clearSeriesWhere(id) { it != UsageHistoryStore.SERIES_CODEX_WEEKLY }
        refreshAccountLists()
    }

    fun renameClaudeAccount(id: String, nickname: String) {
        credentialManager.setAccountNickname(id, nickname)
        refreshAccountLists()
    }

    fun renameCodexAccount(id: String, nickname: String) {
        codexCredentialManager.setAccountNickname(id, nickname)
        refreshAccountLists()
    }

    fun switchClaudeAccount(id: String) {
        if (id == credentialManager.activeAccountId) return
        if (!credentialManager.switchAccount(id)) return
        autoRefreshJob?.cancel()
        onClaudeAccountChanged()
        _lastUpdated.value = null
        checkCredentialsAndLoad()
    }

    /**
     * Resets per-account state after the active Claude account changed, so
     * reset detection and graphs never compare two different accounts.
     */
    private fun onClaudeAccountChanged() {
        prevSessionUtil = null
        prevWeeklyUtil = null
        prevDynamicUtils = emptyMap()
        lastCoachEvalTime = 0L
        refreshAccountLists()
        reloadHistory()
        // The widget shows the active account, so redraw it for the new one
        try {
            UsageWidgetReceiver.updateWidget(getApplication())
        } catch (_: Exception) {
            // Widget might not be placed
        }
    }

    private fun refreshAccountLists() {
        _claudeAccounts.value = AccountList(
            credentialManager.getAccounts().map { it.toSummary() },
            credentialManager.activeAccountId
        )
        _codexAccounts.value = AccountList(
            codexCredentialManager.getAccounts().map { it.toSummary() },
            codexCredentialManager.activeAccountId
        )
    }

    private fun reloadHistory() {
        _usageHistory.value = historyStore.getHistory(
            credentialManager.activeAccountId,
            codexCredentialManager.activeAccountId
        )
    }

    /** Fills in a missing switcher label, e.g. for a login carried over from before accounts existed. */
    private suspend fun ensureClaudeLabel(id: String, credentials: Credentials) {
        if (credentialManager.getAccounts().firstOrNull { it.id == id }?.label?.isNotBlank() != false) return
        val label = repository.fetchAccountLabel(credentials.sessionKey) ?: return
        credentialManager.setAccountLabel(id, label)
        refreshAccountLists()
    }

    // --- Codex ---

    private fun checkCodexCredentials() {
        viewModelScope.launch {
            if (codexCredentialManager.hasCredentials()) {
                fetchCodexUsageData()
            }
        }
    }

    fun onCodexLoginComplete(cookies: String) {
        viewModelScope.launch {
            _codexState.value = CodexUiState.Loading
            val tokenResult = codexRepository.fetchSession(cookies)
            tokenResult.fold(
                onSuccess = { session ->
                    val credentials = CodexCredentials(session.accessToken, cookies)
                    codexCredentialManager.saveCredentials(credentials, session.email)
                    onCodexAccountChanged()
                    fetchCodexUsageData()
                },
                onFailure = { error ->
                    _codexState.value = CodexUiState.Error(
                        message = error.message ?: "Failed to get access token.",
                        isAuthError = error is AuthException
                    )
                }
            )
        }
    }

    private suspend fun fetchCodexUsageData() {
        val accountId = codexCredentialManager.activeAccountId
        val credentials = codexCredentialManager.getCredentials()
        if (accountId == null || credentials == null) {
            _codexState.value = CodexUiState.NotConnected
            return
        }

        _codexState.value = CodexUiState.Loading
        var result = codexRepository.fetchUsageData(credentials)
        if (result.exceptionOrNull() is AuthException) {
            // The access token is short-lived, but the saved session cookies can
            // mint a new one; only drop the account if that fails too
            val session = codexRepository.fetchSession(credentials.sessionCookies).getOrNull()
            if (session != null && codexCredentialManager.activeAccountId == accountId) {
                val renewed = credentials.copy(accessToken = session.accessToken)
                codexCredentialManager.updateCredentials(accountId, renewed)
                result = codexRepository.fetchUsageData(renewed)
            }
        }
        // The user switched ChatGPT accounts while this request was in flight
        if (codexCredentialManager.activeAccountId != accountId) return
        result.fold(
            onSuccess = { data ->
                _codexState.value = CodexUiState.Connected(data)
                recordCodexHistory(accountId, data)
                ensureCodexLabel(accountId, credentials)
                startAutoRefresh()
                // Also merge into main UiState if Claude is already loaded
                val current = _uiState.value
                if (current is UiState.Success) {
                    _uiState.value = current.copy(codexData = data)
                }
            },
            onFailure = { error ->
                val isAuth = error is AuthException
                if (isAuth) {
                    codexCredentialManager.removeAccount(accountId)
                    onCodexAccountChanged()
                }
                _codexState.value = CodexUiState.Error(
                    message = error.message ?: "Failed to fetch Codex usage.",
                    isAuthError = isAuth
                )
            }
        )
    }

    /** Disconnects the active ChatGPT account; the next saved one, if any, takes over. */
    fun logoutCodex() {
        codexCredentialManager.activeAccountId?.let { id ->
            codexCredentialManager.removeAccount(id)
            historyStore.clearSeriesWhere(id) { it == UsageHistoryStore.SERIES_CODEX_WEEKLY }
        }
        onCodexAccountChanged()
        clearCodexData()
        if (codexCredentialManager.hasCredentials()) {
            refreshCodex()
        } else {
            _codexState.value = CodexUiState.NotConnected
        }
    }

    /** Removes a saved ChatGPT account; removing the active one is the same as [logoutCodex]. */
    fun removeCodexAccount(id: String) {
        if (id == codexCredentialManager.activeAccountId) {
            logoutCodex()
            return
        }
        codexCredentialManager.removeAccount(id)
        historyStore.clearSeriesWhere(id) { it == UsageHistoryStore.SERIES_CODEX_WEEKLY }
        refreshAccountLists()
    }

    fun switchCodexAccount(id: String) {
        if (id == codexCredentialManager.activeAccountId) return
        if (!codexCredentialManager.switchAccount(id)) return
        onCodexAccountChanged()
        clearCodexData()
        refreshCodex()
    }

    private fun onCodexAccountChanged() {
        refreshAccountLists()
        reloadHistory()
    }

    /** Removes the previous account's Codex data from the main state. */
    private fun clearCodexData() {
        val current = _uiState.value
        if (current is UiState.Success) {
            _uiState.value = current.copy(codexData = null)
        }
    }

    private suspend fun ensureCodexLabel(id: String, credentials: CodexCredentials) {
        if (codexCredentialManager.getAccounts().firstOrNull { it.id == id }?.label?.isNotBlank() != false) return
        val email = codexRepository.fetchSession(credentials.sessionCookies).getOrNull()?.email
        if (email.isNullOrBlank()) return
        codexCredentialManager.setAccountLabel(id, email)
        refreshAccountLists()
    }

    fun refreshCodex() {
        viewModelScope.launch {
            fetchCodexUsageData()
        }
    }

    private suspend fun fetchUsageData() = fetchMutex.withLock {
        val accountId = credentialManager.activeAccountId
        val credentials = credentialManager.getCredentials()
        if (accountId == null || credentials == null) {
            _uiState.value = UiState.LoginRequired
            return@withLock
        }

        val result = repository.fetchUsageData(credentials)
        // The user switched Claude accounts while this request was in flight
        if (credentialManager.activeAccountId != accountId) return@withLock
        result.fold(
            onSuccess = { data ->
                val codexData = (_codexState.value as? CodexUiState.Connected)?.data
                _uiState.value = UiState.Success(data, codexData)
                _lastUpdated.value = formatLastUpdated()
                startAutoRefresh()
                if (appPreferences.notificationEnabled) {
                    UsageNotificationService.forceUpdate(getApplication())
                }
                // Coach features (push notifications)
                if (appPreferences.coachEnabled) {
                    detectResets(accountId, data)
                    val now = System.currentTimeMillis()
                    if (now - lastCoachEvalTime >= COACH_EVAL_INTERVAL_MS) {
                        evaluateCoachNotification(data)
                        lastCoachEvalTime = now
                    }
                }
                recordUsageHistory(accountId, data)
                ensureClaudeLabel(accountId, credentials)
                // Update previous values for next comparison
                prevSessionUtil = data.fiveHour?.utilization
                prevWeeklyUtil = data.sevenDay?.utilization
                prevDynamicUtils = data.dynamicMetrics.associate { it.key to it.metric.utilization }
            },
            onFailure = { error ->
                val isAuth = error is AuthException
                val isRateLimit = error is RateLimitException
                if (isAuth) {
                    credentialManager.removeAccount(accountId)
                    onClaudeAccountChanged()
                }
                // Only show error if we don't already have data
                val currentState = _uiState.value
                if (currentState is UiState.Success && !isAuth) {
                    // Keep existing data, just update timestamp note
                    val reason = if (isRateLimit) "Rate limited" else "Update failed"
                    _lastUpdated.value = "$reason - ${formatLastUpdated()}"
                } else {
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to fetch usage data.",
                        isAuthError = isAuth
                    )
                }
            }
        )
    }

    private fun startAutoRefresh() {
        if (!isAppInForeground) return
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                if (!isAppInForeground) break
                fetchUsageData()
                if (codexCredentialManager.hasCredentials()) {
                    fetchCodexUsageData()
                }
            }
        }
    }

    private fun formatLastUpdated(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }

    private fun evaluateCoachNotification(data: UsageData) {
        val sessionUtil = data.fiveHour?.utilization ?: 0.0
        val sessionRemaining = data.fiveHour?.remainingDuration
        val weeklyUtil = data.sevenDay?.utilization ?: 0.0
        val weeklyRemaining = data.sevenDay?.remainingDuration
        // Any per-model weekly limit above 80% (seven_day_sonnet, seven_day_fable, ...)
        val hotModelMetric = data.dynamicMetrics.firstOrNull {
            it.key.startsWith("seven_day_") && it.metric.utilization > 80.0
        }

        val (title, message) = when {
            // Session maxed out
            sessionUtil >= 100.0 -> {
                val timeStr = sessionRemaining?.let { formatDuration(it) } ?: ""
                "Session maxed out" to "Resets in $timeStr — switch tasks or take a break!"
            }
            // Session > 80% & reset < 30min
            sessionUtil > 80.0 && sessionRemaining != null
                && sessionRemaining < Duration.ofMinutes(30) -> {
                "Reset imminent" to "Take a break, come back fully charged"
            }
            // Session < 30% & reset < 1h
            sessionUtil < 30.0 && sessionUtil > 0.0 && sessionRemaining != null
                && sessionRemaining < Duration.ofHours(1) -> {
                val timeStr = formatDuration(sessionRemaining)
                "Session resets in $timeStr" to "Don't waste the remaining capacity!"
            }
            // Per-model weekly limit > 80%
            hotModelMetric != null -> {
                val shortName = hotModelMetric.label.substringBefore(" (")
                "$shortName at ${hotModelMetric.metric.utilization.toInt()}%" to
                    "Consider switching to other models"
            }
            // Weekly > 70% & reset > 2 days
            weeklyUtil > 70.0 && weeklyRemaining != null
                && weeklyRemaining > Duration.ofDays(2) -> {
                "Weekly at ${weeklyUtil.toInt()}%" to "Focus on high-impact tasks only"
            }
            // Weekly < 40% & reset < 1 day
            weeklyUtil < 40.0 && weeklyUtil > 0.0 && weeklyRemaining != null
                && weeklyRemaining < Duration.ofDays(1) -> {
                "Last sprint!" to "Use your remaining weekly capacity before reset"
            }
            // Weekly < 50% & reset > 3 days
            weeklyUtil < 50.0 && weeklyRemaining != null
                && weeklyRemaining > Duration.ofDays(3) -> {
                "Plenty of capacity" to "Perfect time for deep work!"
            }
            else -> return
        }
        sendCoachPushNotification(title, message, COACH_ANALYSIS_ID)
    }

    private fun detectResets(accountId: String, data: UsageData) {
        // Session reset: previous utilization was significant, now dropped to near zero
        val currentSessionUtil = data.fiveHour?.utilization ?: 0.0
        val prevSession = prevSessionUtil
        if (prevSession != null && prevSession > 30.0 && currentSessionUtil < 5.0) {
            sendCoachPushNotification(
                "Fully charged!",
                "Session reset complete — start a new session now",
                COACH_SESSION_RESET_ID
            )
        }

        // Weekly reset: previous utilization was significant, now dropped to near zero
        val currentWeeklyUtil = data.sevenDay?.utilization ?: 0.0
        val prevWeekly = prevWeeklyUtil
        if (prevWeekly != null && prevWeekly > 30.0 && currentWeeklyUtil < 5.0) {
            sendCoachPushNotification(
                "Weekly reset!",
                "Fresh weekly capacity — let's make this week count",
                COACH_WEEKLY_RESET_ID
            )
            // Only Claude series reset here; Codex has its own weekly window
            historyStore.clearSeriesWhere(accountId) { it != UsageHistoryStore.SERIES_CODEX_WEEKLY }
            reloadHistory()
        }

        // Per-model resets (seven_day_sonnet, seven_day_fable, ...)
        data.dynamicMetrics.forEach { labeled ->
            if (!labeled.key.startsWith("seven_day_")) return@forEach
            val prev = prevDynamicUtils[labeled.key] ?: return@forEach
            if (prev > 30.0 && labeled.metric.utilization < 5.0) {
                val shortName = labeled.label.substringBefore(" (")
                sendCoachPushNotification(
                    "$shortName reset!",
                    "$shortName weekly limit refreshed",
                    // Stable per-key notification ID so different limits don't overwrite each other
                    COACH_MODEL_RESET_BASE_ID + (labeled.key.hashCode() and 0x3FF)
                )
            }
        }
    }

    private fun recordUsageHistory(accountId: String, data: UsageData) {
        val values = buildMap {
            data.sevenDay?.let { put(UsageHistoryStore.SERIES_WEEKLY_ALL, it.utilization) }
            // Per-model weekly limits (seven_day_fable, limits_fable, ...)
            data.dynamicMetrics
                .filter { it.key.startsWith("seven_day_") || it.label.endsWith("(7d)") }
                .forEach { put(it.key, it.metric.utilization) }
        }
        if (values.isEmpty()) return
        historyStore.addEntries(accountId, System.currentTimeMillis(), values)
        reloadHistory()
    }

    private fun recordCodexHistory(accountId: String, data: CodexUsageData) {
        val weekly = listOfNotNull(data.primaryWindow, data.secondaryWindow)
            .firstOrNull { it.windowLabel == "weekly" } ?: return
        historyStore.addEntries(
            accountId,
            System.currentTimeMillis(),
            mapOf(UsageHistoryStore.SERIES_CODEX_WEEKLY to weekly.usedPercent)
        )
        reloadHistory()
    }

    private fun createCoachNotificationChannel() {
        val channel = NotificationChannel(
            COACH_CHANNEL_ID,
            "Productivity Coach",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Smart usage tips and reset alerts"
        }
        val manager = getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun sendCoachPushNotification(title: String, message: String, notificationId: Int) {
        val app = getApplication<Application>()
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, COACH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        if (totalSeconds <= 0) return "0m"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    private fun <T> Account<T>.toSummary() = AccountSummary(id, label, nickname)

    companion object {
        const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val COACH_EVAL_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val COACH_CHANNEL_ID = "coach_channel"
        private const val COACH_SESSION_RESET_ID = 2001
        private const val COACH_WEEKLY_RESET_ID = 2002
        private const val COACH_ANALYSIS_ID = 2010
        // Per-model reset IDs occupy 3000..3000+0x3FF (keyed by metric key hash)
        private const val COACH_MODEL_RESET_BASE_ID = 3000
    }
}
