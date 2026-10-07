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

        // The notification follows the primary provider; in ChatGPT mode we fall
        // back to Claude when no ChatGPT account is connected.
        var notification: Notification? = null

        if (chatGptMode) {
            val codexCredentials = CodexCredentialManager(applicationContext).getCredentials()
            if (codexCredentials != null) {
                val codexData = CodexUsageRepository().fetchUsageData(codexCredentials).getOrNull()
                    ?: return Result.retry()
                notification = UsageNotificationService
                    .buildCodexNotification(applicationContext, codexData)
            }
        }

        if (notification == null) {
            val credentials = CredentialManager(applicationContext).getCredentials()
                ?: return if (chatGptMode) Result.success() else Result.failure()
            val data = UsageRepository().fetchUsageData(credentials).getOrNull()
                ?: return Result.retry()
            notification = UsageNotificationService.buildUsageNotification(applicationContext, data)
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
                UsageNotificationService.ensureChannel(applicationContext)
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                manager.notify(UsageNotificationService.NOTIFICATION_ID, notification)
            } catch (_: Exception) {
                // Notification update is best-effort
            }
        }

        return Result.success()
    }
}
