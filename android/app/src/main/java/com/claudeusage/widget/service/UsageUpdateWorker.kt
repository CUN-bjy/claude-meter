package com.claudeusage.widget.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CodexCredentialManager
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.repository.CodexUsageRepository
import com.claudeusage.widget.data.repository.UsageRepository
import com.claudeusage.widget.widget.UsageWidgetReceiver

class UsageUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val chatGptMode = prefs.primaryMode == AppPreferences.MODE_CHATGPT

        // The notification follows the primary provider and falls back to the
        // other one, so a user with a single account of either kind keeps
        // getting updates whichever mode they are in.
        val providers = if (chatGptMode) {
            listOf(::fetchCodexNotification, ::fetchClaudeNotification)
        } else {
            listOf(::fetchClaudeNotification, ::fetchCodexNotification)
        }
        var notification: Notification? = null
        for (fetch in providers) {
            val result = fetch() ?: continue // no account for this provider
            notification = result.getOrNull() ?: return Result.retry()
            break
        }

        // Trigger widget update (the widget fetches for the active mode itself)
        try {
            UsageWidgetReceiver.updateWidget(applicationContext)
        } catch (_: Exception) {
            // Widget might not be placed
        }

        // Update persistent notification if enabled
        if (prefs.notificationEnabled) {
            try {
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                if (notification == null) {
                    // No account of either provider is left to show
                    manager.cancel(UsageNotificationService.NOTIFICATION_ID)
                } else {
                    UsageNotificationService.ensureChannel(applicationContext)
                    manager.notify(UsageNotificationService.NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {
                // Notification update is best-effort
            }
        }

        return Result.success()
    }

    /** Null when no Claude account is saved; otherwise the fetch outcome. */
    private suspend fun fetchClaudeNotification(): kotlin.Result<Notification>? {
        val credentials = CredentialManager(applicationContext).getCredentials() ?: return null
        return UsageRepository().fetchUsageData(credentials).map { data ->
            UsageNotificationService.buildUsageNotification(applicationContext, data)
        }
    }

    /** Null when no ChatGPT account is saved; otherwise the fetch outcome. */
    private suspend fun fetchCodexNotification(): kotlin.Result<Notification>? {
        val credentials = CodexCredentialManager(applicationContext).getCredentials() ?: return null
        return CodexUsageRepository().fetchUsageData(credentials).map { data ->
            UsageNotificationService.buildCodexNotification(applicationContext, data)
        }
    }
}
