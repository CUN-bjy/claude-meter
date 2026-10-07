package com.claudeusage.widget.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CodexCredentialManager
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.CodexUsageData
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.UsageMetric
import com.claudeusage.widget.data.repository.CodexUsageRepository
import com.claudeusage.widget.data.repository.UsageRepository
import kotlinx.coroutines.*

class UsageNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = UsageRepository()
    private val codexRepository = CodexUsageRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildSimpleNotification(this, "Loading usage data..."))
        scope.launch {
            try {
                updateNotification()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update notification", e)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claude Meter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current Claude usage status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private suspend fun updateNotification() {
        val prefs = AppPreferences(applicationContext)
        val notification = if (prefs.primaryMode == AppPreferences.MODE_CHATGPT) {
            buildCodexNotificationOrNull() ?: buildClaudeNotificationOrNull()
        } else {
            buildClaudeNotificationOrNull()
        } ?: return

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun buildClaudeNotificationOrNull(): Notification? {
        val credentials = CredentialManager(applicationContext).getCredentials() ?: return null
        val data = repository.fetchUsageData(credentials).getOrNull() ?: return null
        return buildUsageNotification(this@UsageNotificationService, data)
    }

    private suspend fun buildCodexNotificationOrNull(): Notification? {
        val credentials = CodexCredentialManager(applicationContext).getCredentials() ?: return null
        val data = codexRepository.fetchUsageData(credentials).getOrNull() ?: return null
        return buildCodexNotification(this@UsageNotificationService, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.cancelPendingRequests()
        scope.cancel()
    }

    companion object {
        private const val TAG = "UsageNotificationService"
        const val CHANNEL_ID = "claude_usage_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Cannot start foreground service from background", e)
                } else {
                    throw e
                }
            }
        }

        fun stop(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(NOTIFICATION_ID)
            val intent = Intent(context, UsageNotificationService::class.java)
            context.stopService(intent)
        }

        fun forceUpdate(context: Context) {
            start(context)
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claude Meter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current Claude usage status"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        fun buildUsageNotification(context: Context, data: UsageData): Notification {
            val remoteViews = RemoteViews(context.packageName, R.layout.notification_usage)

            if (data.fiveHour != null) {
                val progress = data.fiveHour.utilization.toInt().coerceIn(0, 100)
                remoteViews.setProgressBar(R.id.progress_5h, 100, progress, false)
                remoteViews.setTextViewText(R.id.percent_5h, String.format("%.1f%%", data.fiveHour.utilization))
                remoteViews.setTextViewText(R.id.time_5h, formatRemaining(data.fiveHour))
            }

            if (data.sevenDay != null) {
                val progress = data.sevenDay.utilization.toInt().coerceIn(0, 100)
                remoteViews.setProgressBar(R.id.progress_7d, 100, progress, false)
                remoteViews.setTextViewText(R.id.percent_7d, String.format("%.1f%%", data.sevenDay.utilization))
                remoteViews.setTextViewText(R.id.time_7d, formatRemaining(data.sevenDay))
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()
        }

        fun buildCodexNotification(context: Context, data: CodexUsageData): Notification {
            val remoteViews = RemoteViews(context.packageName, R.layout.notification_usage)

            data.primaryWindow?.let { window ->
                val progress = window.usedPercent.toInt().coerceIn(0, 100)
                remoteViews.setProgressBar(R.id.progress_5h, 100, progress, false)
                remoteViews.setTextViewText(R.id.percent_5h, String.format("%.1f%%", window.usedPercent))
                remoteViews.setTextViewText(R.id.time_5h, formatRemaining(window.resetAt))
            }

            data.secondaryWindow?.let { window ->
                val progress = window.usedPercent.toInt().coerceIn(0, 100)
                remoteViews.setProgressBar(R.id.progress_7d, 100, progress, false)
                remoteViews.setTextViewText(R.id.percent_7d, String.format("%.1f%%", window.usedPercent))
                remoteViews.setTextViewText(R.id.time_7d, formatRemaining(window.resetAt))
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()
        }

        fun buildSimpleNotification(context: Context, text: String): Notification {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun formatRemaining(resetAt: java.time.Instant?): String {
            val target = resetAt ?: return ""
            val remaining = java.time.Duration.between(java.time.Instant.now(), target)
            if (remaining.isNegative) return ""
            return formatRemaining(remaining)
        }

        private fun formatRemaining(metric: UsageMetric): String {
            val remaining = metric.remainingDuration ?: return ""
            return formatRemaining(remaining)
        }

        private fun formatRemaining(remaining: java.time.Duration): String {
            if (remaining.seconds <= 0) return ""
            val d = remaining.seconds / 86400
            val h = (remaining.seconds % 86400) / 3600
            val m = (remaining.seconds % 3600) / 60
            return when {
                d > 0 -> "${d}d ${h}h"
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m"
                else -> ""
            }
        }
    }
}
