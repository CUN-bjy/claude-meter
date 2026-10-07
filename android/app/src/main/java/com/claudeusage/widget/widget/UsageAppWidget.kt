package com.claudeusage.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CodexCredentialManager
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.CodexUsageData
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.UsageMetric
import com.claudeusage.widget.data.repository.CodexUsageRepository
import com.claudeusage.widget.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class UsageAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chatGptMode = AppPreferences(context).primaryMode == AppPreferences.MODE_CHATGPT

        // In ChatGPT mode the widget leads with Codex; it falls back to Claude
        // when no ChatGPT account is connected.
        val codexData = if (chatGptMode) fetchCodexData(context) else null
        val usageData = if (codexData == null) fetchClaudeData(context) else null

        provideContent {
            if (codexData != null) {
                CodexWidgetContent(codexData)
            } else {
                WidgetContent(usageData)
            }
        }
    }

    private suspend fun fetchClaudeData(context: Context): UsageData? = try {
        val credentials = CredentialManager(context).getCredentials()
        if (credentials != null) {
            withContext(Dispatchers.IO) {
                UsageRepository().fetchUsageData(credentials).getOrNull()
            }
        } else null
    } catch (e: Exception) {
        null
    }

    private suspend fun fetchCodexData(context: Context): CodexUsageData? = try {
        val credentials = CodexCredentialManager(context).getCredentials()
        if (credentials != null) {
            withContext(Dispatchers.IO) {
                CodexUsageRepository().fetchUsageData(credentials).getOrNull()
            }
        } else null
    } catch (e: Exception) {
        null
    }
}

// -- Colors --
private val BgDark = ColorProvider(Color(0xFF111118))
private val BgCard = ColorProvider(Color(0xFF1C1C2E))
private val BgTrack = ColorProvider(Color(0xFF2A2A40))
private val TextWhite = ColorProvider(Color(0xFFFFFFFF))
private val TextLight = ColorProvider(Color(0xFFE8E6F0))
private val TextDim = ColorProvider(Color(0xFFA09BB0))
private val TextMuted = ColorProvider(Color(0xFF6B6680))
private val AccentPurple = ColorProvider(Color(0xFF8B6FDB))
private val CodexGreen = Color(0xFF10A37F)
private val ClaudePurple = Color(0xFF6B4FBB)

private fun getStatusColor(utilization: Double, normalColor: Color = ClaudePurple): Color {
    return when {
        utilization >= 90.0 -> Color(0xFFE85454)
        utilization >= 75.0 -> Color(0xFFE8943A)
        else -> normalColor
    }
}

private fun getStatusEmoji(utilization: Double): String {
    return when {
        utilization >= 90.0 -> "\uD83D\uDD34"
        utilization >= 75.0 -> "\uD83D\uDFE0"
        else -> "\uD83D\uDFE2"
    }
}

// -- Main Widget --
@Composable
private fun WidgetContent(usageData: UsageData?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDark)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp)
    ) {
        if (usageData == null) {
            NoDataContent()
        } else {
            UsageDataContent(usageData)
        }
    }
}

// -- No Data State --
@Composable
private fun NoDataContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2728",
            style = TextStyle(fontSize = 24.sp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Claude Meter",
            style = TextStyle(
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to sign in",
            style = TextStyle(
                color = TextDim,
                fontSize = 13.sp
            )
        )
    }
}

// -- Data Content --
@Composable
private fun UsageDataContent(data: UsageData) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u26A1 Claude Meter",
                style = TextStyle(
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            val sessionUtil = data.fiveHour?.utilization ?: 0.0
            Text(
                text = getStatusEmoji(sessionUtil),
                style = TextStyle(fontSize = 12.sp)
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Session Card
        UsageCard(
            label = "Session (5h)",
            metric = data.fiveHour
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Weekly Card
        UsageCard(
            label = "Weekly (7d)",
            metric = data.sevenDay
        )

        // Coach message
        val coachMsg = getCoachMessage(data)
        if (coachMsg != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = coachMsg,
                style = TextStyle(
                    color = TextDim,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

// -- Usage Card --
@Composable
private fun UsageCard(label: String, metric: UsageMetric?) {
    ValueCard(
        label = label,
        utilization = metric?.utilization ?: 0.0,
        remaining = metric?.remainingDuration,
        normalColor = ClaudePurple
    )
}

@Composable
private fun ValueCard(
    label: String,
    utilization: Double,
    remaining: Duration?,
    normalColor: Color
) {
    val statusColor = getStatusColor(utilization, normalColor)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(BgCard)
            .cornerRadius(10.dp)
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Top row: label + percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = TextDim,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = String.format("%.0f%%", utilization),
                    style = TextStyle(
                        color = ColorProvider(statusColor),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress bar
            WidgetProgressBar(utilization = utilization, statusColor = statusColor)

            // Reset time
            if (remaining != null && remaining > Duration.ZERO) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Resets in ${formatDuration(remaining)}",
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

// -- Progress Bar (segmented approach for Glance weight support) --
@Composable
private fun WidgetProgressBar(utilization: Double, statusColor: Color) {
    val totalSegments = 20
    val filledSegments = ((utilization / 100.0) * totalSegments).toInt().coerceIn(0, totalSegments)
    val emptySegments = totalSegments - filledSegments

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(8.dp)
            .cornerRadius(4.dp)
            .background(BgTrack)
    ) {
        repeat(filledSegments) {
            Spacer(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(8.dp)
                    .background(ColorProvider(statusColor))
            )
        }
        repeat(emptySegments) {
            Spacer(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(8.dp)
            )
        }
    }
}

// -- Helpers --
private fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    return when {
        totalMinutes < 60 -> "${totalMinutes}m"
        totalMinutes < 1440 -> {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
        else -> {
            val days = totalMinutes / 1440
            val hours = (totalMinutes % 1440) / 60
            if (hours > 0) "${days}d ${hours}h" else "${days}d"
        }
    }
}

private fun getCoachMessage(data: UsageData): String? {
    val sessionUtil = data.fiveHour?.utilization ?: 0.0
    val sessionRemaining = data.fiveHour?.remainingDuration
    val weeklyUtil = data.sevenDay?.utilization ?: 0.0
    val weeklyRemaining = data.sevenDay?.remainingDuration

    return when {
        sessionUtil >= 100.0 -> "\u26A0\uFE0F Session maxed — switch tasks!"
        sessionUtil > 80.0 && sessionRemaining != null
            && sessionRemaining < Duration.ofMinutes(30) -> "\uD83D\uDCA1 Reset imminent — take a break"
        weeklyUtil > 70.0 && weeklyRemaining != null
            && weeklyRemaining > Duration.ofDays(2) -> "\uD83D\uDCA1 70%+ weekly — focus on high-impact"
        weeklyUtil < 50.0 && weeklyRemaining != null
            && weeklyRemaining > Duration.ofDays(3) -> "\uD83D\uDE80 Plenty of capacity!"
        else -> null
    }
}

// -- Codex (ChatGPT mode) Content --
@Composable
private fun CodexWidgetContent(data: CodexUsageData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDark)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u26A1 Codex Meter",
                    style = TextStyle(
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = getStatusEmoji(data.primaryWindow?.usedPercent ?: 0.0),
                    style = TextStyle(fontSize = 12.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            ValueCard(
                label = "Session (5h)",
                utilization = data.primaryWindow?.usedPercent ?: 0.0,
                remaining = data.primaryWindow?.resetAt?.let {
                    Duration.between(java.time.Instant.now(), it)
                },
                normalColor = CodexGreen
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            ValueCard(
                label = "Weekly (7d)",
                utilization = data.secondaryWindow?.usedPercent ?: 0.0,
                remaining = data.secondaryWindow?.resetAt?.let {
                    Duration.between(java.time.Instant.now(), it)
                },
                normalColor = CodexGreen
            )

            if (data.limitReached) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "\u26A0\uFE0F Rate limit reached",
                    style = TextStyle(color = TextDim, fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}
