package com.claudeusage.widget.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.model.CodexUsageData
import com.claudeusage.widget.data.model.ExtraUsageInfo
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.CoachNotification
import com.claudeusage.widget.data.model.CoachSeverity
import com.claudeusage.widget.ui.components.BannerAd
import com.claudeusage.widget.ui.components.CoachBanner
import com.claudeusage.widget.ui.components.UsageProgressBar
import com.claudeusage.widget.ui.theme.*
import java.time.Duration
import java.time.Instant

private val ACCOUNT_HINT = CoachNotification(
    message = "New: tap your email at the top to switch accounts or add another " +
        "Claude or ChatGPT account.",
    severity = CoachSeverity.INFO
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    uiState: UiState,
    isRefreshing: Boolean,
    lastUpdated: String?,
    hiddenMetrics: Set<String>,
    codexState: CodexUiState = CodexUiState.NotConnected,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
    onManualLogin: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onForecastClick: () -> Unit = {},
    onCodexLoginClick: () -> Unit = {},
    onCodexLogout: () -> Unit = {},
    claudeAccounts: AccountList = AccountList(),
    onSwitchAccount: (String) -> Unit = {},
    codexAccounts: AccountList = AccountList(),
    onSwitchCodexAccount: (String) -> Unit = {},
    showAccountHint: Boolean = false,
    onDismissAccountHint: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val claudeMenuOpen = remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        val activeLabel = claudeAccounts.active?.let { claudeAccounts.labelOf(it) }
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (activeLabel != null && claudeAccounts.accounts.size > 1) {
                        "Log out of $activeLabel? You'll switch to your next saved account."
                    } else {
                        "Are you sure you want to logout?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (claudeAccounts.accounts.isEmpty()) {
                        Text(
                            text = "Claude Meter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        Column {
                            Text(
                                text = "Claude Meter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            // Email + arrow, like the ChatGPT card, so it reads as an account menu
                            AccountSwitcher(
                                accounts = claudeAccounts,
                                accentColor = ClaudePurpleLight,
                                addLabel = "Add Claude account",
                                onSwitch = onSwitchAccount,
                                onAdd = onLoginClick,
                                expandedState = claudeMenuOpen,
                                onOpen = onDismissAccountHint
                            ) {
                                Text(
                                    text = claudeAccounts.active?.let { claudeAccounts.labelOf(it) }
                                        ?: "Choose account",
                                    color = ExtendedTheme.colors.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState is UiState.Success) {
                        IconButton(onClick = onForecastClick) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = "Usage Forecast",
                                tint = ExtendedTheme.colors.textSecondary
                            )
                        }
                    }
                    // Always reachable: on an error or after a session expires, Settings
                    // is where the user switches or removes accounts
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ExtendedTheme.colors.textSecondary
                        )
                    }
                    if (uiState is UiState.Success) {
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = ClaudePurpleLight
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = ExtendedTheme.colors.textSecondary
                                )
                            }
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Logout",
                                tint = ExtendedTheme.colors.textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is UiState.Loading -> LoadingContent()
                is UiState.LoginRequired -> LoginContent(
                    onLoginClick = onLoginClick,
                    onManualLogin = onManualLogin
                )
                is UiState.Success -> UsageContent(
                    data = uiState.data,
                    lastUpdated = lastUpdated,
                    hiddenMetrics = hiddenMetrics,
                    codexState = codexState,
                    onCodexLoginClick = onCodexLoginClick,
                    onCodexLogout = onCodexLogout,
                    codexAccounts = codexAccounts,
                    onSwitchCodexAccount = onSwitchCodexAccount,
                    showAccountHint = showAccountHint && claudeAccounts.accounts.isNotEmpty(),
                    onAccountHintClick = {
                        onDismissAccountHint()
                        claudeMenuOpen.value = true
                    },
                    onDismissAccountHint = onDismissAccountHint
                )
                is UiState.Error -> ErrorContent(
                    message = uiState.message,
                    isAuthError = uiState.isAuthError,
                    onRetry = if (uiState.isAuthError) onLoginClick else onRefresh
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Ring logo from the app icon
        val ringSize = 180.dp
        val ringColor = ClaudePurple
        val ringColorLight = ClaudePurpleLight
        val gapColor = Color.White

        Canvas(modifier = Modifier.size(ringSize)) {
            val canvasSize = size.minDimension
            val c = Offset(canvasSize / 2, canvasSize / 2)
            val outerRadius = canvasSize * 0.45f
            val sw = canvasSize * 0.13f
            val midRadius = outerRadius - sw / 2
            val arcRect = Size(midRadius * 2, midRadius * 2)
            val arcTopLeft = Offset(c.x - midRadius, c.y - midRadius)

            // Purple ring arc (main portion ~75%)
            drawArc(
                color = ringColor,
                startAngle = 290f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Butt),
                topLeft = arcTopLeft,
                size = arcRect
            )

            // White gap arc (~25%)
            drawArc(
                color = gapColor,
                startAngle = 200f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Butt),
                topLeft = arcTopLeft,
                size = arcRect
            )
        }

        // Small 4-point star at bottom-right
        Text(
            text = "\u2726",
            color = ExtendedTheme.colors.textMuted,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
}

@Composable
private fun LoginContent(
    onLoginClick: () -> Unit,
    onManualLogin: (String) -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var sessionKeyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ring logo
        Canvas(modifier = Modifier.size(80.dp)) {
            val s = size.minDimension
            val c = Offset(s / 2, s / 2)
            val outerR = s * 0.45f
            val sw = s * 0.13f
            val midR = outerR - sw / 2
            val rect = Size(midR * 2, midR * 2)
            val tl = Offset(c.x - midR, c.y - midR)

            drawArc(
                color = ClaudePurple,
                startAngle = 290f, sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Butt),
                topLeft = tl, size = rect
            )
            drawArc(
                color = Color.White,
                startAngle = 200f, sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Butt),
                topLeft = tl, size = rect
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Claude Meter",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in to monitor your Claude usage",
            fontSize = 14.sp,
            color = ExtendedTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClaudePurple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Sign in with Claude.ai",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showManualInput = !showManualInput },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ExtendedTheme.colors.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Enter session key manually",
                fontSize = 14.sp
            )
        }

        AnimatedVisibility(visible = showManualInput) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                OutlinedTextField(
                    value = sessionKeyInput,
                    onValueChange = { sessionKeyInput = it },
                    label = { Text("Session Key") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClaudePurple,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedLabelColor = ClaudePurple,
                        cursorColor = ClaudePurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (sessionKeyInput.isNotBlank()) {
                            onManualLogin(sessionKeyInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sessionKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClaudePurpleDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun UsageContent(
    data: UsageData,
    lastUpdated: String?,
    hiddenMetrics: Set<String>,
    codexState: CodexUiState = CodexUiState.NotConnected,
    onCodexLoginClick: () -> Unit = {},
    onCodexLogout: () -> Unit = {},
    codexAccounts: AccountList = AccountList(),
    onSwitchCodexAccount: (String) -> Unit = {},
    showAccountHint: Boolean = false,
    onAccountHintClick: () -> Unit = {},
    onDismissAccountHint: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // One-time pointer to the account menu for people updating from a single-account version
        CoachBanner(
            notification = if (showAccountHint) ACCOUNT_HINT else null,
            onClick = onAccountHintClick,
            onDismiss = onDismissAccountHint,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Main usage cards - always show even when null (after reset)
        val defaultMetric = com.claudeusage.widget.data.model.UsageMetric(0.0, null)

        UsageCard(
            title = "Current Session",
            subtitle = "5-hour window",
            metric = data.fiveHour ?: defaultMetric,
            totalWindowHours = 5.0
        )
        Spacer(modifier = Modifier.height(12.dp))

        UsageCard(
            title = "Weekly Limit",
            subtitle = "7-day window",
            metric = data.sevenDay ?: defaultMetric,
            totalWindowHours = 168.0
        )

        // Extra metrics (filtered by settings)
        val filteredMetrics = data.extraMetrics.filter { it.key !in hiddenMetrics }
        if (filteredMetrics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            filteredMetrics.forEach { labeled ->
                Spacer(modifier = Modifier.height(8.dp))
                if (labeled.key == "extra_usage") {
                    MiniUsageCard(
                        label = labeled.label,
                        metric = labeled.metric,
                        extraUsageInfo = data.extraUsageInfo
                    )
                } else {
                    MiniUsageCard(label = labeled.label, metric = labeled.metric)
                }
            }
        }

        // Codex usage section (toggled by settings)
        if (AppPreferences.CODEX_METRIC_KEY !in hiddenMetrics) {
            Spacer(modifier = Modifier.height(20.dp))
            CodexSection(
                codexState = codexState,
                onCodexLoginClick = onCodexLoginClick,
                onCodexLogout = onCodexLogout,
                codexAccounts = codexAccounts,
                onSwitchCodexAccount = onSwitchCodexAccount
            )
        }

        // Last updated
        if (lastUpdated != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Last updated at $lastUpdated",
                color = ExtendedTheme.colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Banner Ad
        Spacer(modifier = Modifier.height(16.dp))
        BannerAd(
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun UsageCard(
    title: String,
    subtitle: String,
    metric: com.claudeusage.widget.data.model.UsageMetric,
    totalWindowHours: Double = 5.0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            UsageProgressBar(
                label = "",
                utilization = metric.utilization,
                statusLevel = metric.statusLevel,
                remainingDuration = metric.remainingDuration,
                totalWindowHours = totalWindowHours
            )
        }
    }
}

@Composable
private fun MiniUsageCard(
    label: String,
    metric: com.claudeusage.widget.data.model.UsageMetric,
    totalWindowHours: Double = 168.0,
    extraUsageInfo: ExtraUsageInfo? = null
) {
    val isExtra = extraUsageInfo != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isExtra) {
                ExtraUsageBar(
                    label = label,
                    utilization = metric.utilization,
                    info = extraUsageInfo!!
                )
            } else {
                UsageProgressBar(
                    label = label,
                    utilization = metric.utilization,
                    statusLevel = metric.statusLevel,
                    remainingDuration = metric.remainingDuration,
                    totalWindowHours = totalWindowHours
                )
            }
        }
    }
}

@Composable
private fun ExtraUsageBar(
    label: String,
    utilization: Double,
    info: ExtraUsageInfo
) {
    val barColor = StatusExtra
    val gradient = Brush.horizontalGradient(
        colors = listOf(StatusExtra, StatusExtraLight)
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (utilization / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "extra_progress"
    )

    val progressTrackColor = ExtendedTheme.colors.progressTrack

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ExtendedTheme.colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Prepaid balance
                if (info.balanceCents != null) {
                    Text(
                        text = "Bal \$${info.balanceCents / 100}",
                        color = StatusExtraLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // Spending or percentage
                if (info.usedCents != null && info.limitCents != null) {
                    Text(
                        text = "\$${info.usedCents / 100}/\$${info.limitCents / 100}",
                        color = barColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "${String.format("%.1f", utilization)}%",
                        color = barColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cr = CornerRadius(5.dp.toPx())

                drawRoundRect(
                    color = progressTrackColor,
                    cornerRadius = cr
                )

                if (animatedProgress > 0f) {
                    drawRoundRect(
                        brush = gradient,
                        size = Size(size.width * animatedProgress, size.height),
                        cornerRadius = cr
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexSection(
    codexState: CodexUiState,
    onCodexLoginClick: () -> Unit,
    onCodexLogout: () -> Unit,
    codexAccounts: AccountList = AccountList(),
    onSwitchCodexAccount: (String) -> Unit = {}
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Codex", fontWeight = FontWeight.Bold) },
            text = {
                val activeLabel = codexAccounts.active?.let { codexAccounts.labelOf(it) }
                Text(
                    if (activeLabel != null && codexAccounts.accounts.size > 1) {
                        "Disconnect $activeLabel? You'll switch to your next saved ChatGPT account."
                    } else {
                        "Are you sure you want to disconnect your ChatGPT account?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    onCodexLogout()
                }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Codex Usage",
                            color = CodexGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(beta)",
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    val activeCodex = codexAccounts.active
                    if (activeCodex == null) {
                        Text(
                            text = "ChatGPT / Codex",
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 11.sp
                        )
                    } else {
                        AccountSwitcher(
                            accounts = codexAccounts,
                            accentColor = CodexGreen,
                            addLabel = "Add ChatGPT account",
                            onSwitch = onSwitchCodexAccount,
                            onAdd = onCodexLoginClick
                        ) {
                            Text(
                                text = codexAccounts.labelOf(activeCodex),
                                color = ExtendedTheme.colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (codexState is CodexUiState.Connected) {
                    TextButton(onClick = { showDisconnectDialog = true }) {
                        Text(
                            text = "Disconnect",
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (codexState) {
                is CodexUiState.NotConnected -> {
                    OutlinedButton(
                        onClick = onCodexLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CodexGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Connect ChatGPT account",
                            fontSize = 14.sp
                        )
                    }
                }
                is CodexUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = CodexGreen
                        )
                    }
                }
                is CodexUiState.Connected -> {
                    CodexUsageBar(data = codexState.data)
                }
                is CodexUiState.Error -> {
                    Text(
                        text = codexState.message,
                        color = StatusCritical,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = if (codexState.isAuthError) onCodexLoginClick else onCodexLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CodexGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (codexState.isAuthError) "Reconnect" else "Retry",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows [content] with a dropdown arrow; tapping it lists the saved
 * accounts (active one checked) plus an entry to log in to another one.
 */
@Composable
private fun AccountSwitcher(
    accounts: AccountList,
    accentColor: Color,
    addLabel: String,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    expandedState: MutableState<Boolean> = remember { mutableStateOf(false) },
    onOpen: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var expanded by expandedState

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    expanded = true
                    onOpen()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f, fill = false)) { content() }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Switch account",
                tint = ExtendedTheme.colors.textMuted
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.accounts.forEach { account ->
                val isActive = account.id == accounts.activeId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = accounts.labelOf(account),
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        if (isActive) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = accentColor)
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSwitch(account.id)
                    }
                )
            }
            Divider(color = ExtendedTheme.colors.dividerColor)
            DropdownMenuItem(
                text = { Text(addLabel) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = accentColor)
                },
                onClick = {
                    expanded = false
                    onAdd()
                }
            )
        }
    }
}

@Composable
private fun CodexUsageBar(data: CodexUsageData) {
    val progressTrackColor = ExtendedTheme.colors.progressTrack

    Column(modifier = Modifier.fillMaxWidth()) {
        // Limit reached warning
        if (data.limitReached) {
            Text(
                text = "Rate limit reached",
                color = StatusCritical,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Primary window (5h)
        data.primaryWindow?.let { window ->
            CodexWindowRow(
                label = "5h window",
                usedPercent = window.usedPercent,
                resetAt = window.resetAt,
                progressTrackColor = progressTrackColor
            )
        }

        // Secondary window (weekly)
        data.secondaryWindow?.let { window ->
            if (data.primaryWindow != null) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            CodexWindowRow(
                label = "Weekly",
                usedPercent = window.usedPercent,
                resetAt = window.resetAt,
                progressTrackColor = progressTrackColor
            )
        }

        // Plan type
        if (data.planType != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Plan: ${data.planType}",
                color = ExtendedTheme.colors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun CodexWindowRow(
    label: String,
    usedPercent: Double,
    resetAt: Instant?,
    progressTrackColor: Color
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(CodexGreen, CodexGreenLight)
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (usedPercent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "codex_progress_$label"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 12.sp
        )
        Text(
            text = "${String.format("%.1f", usedPercent)}%",
            color = if (usedPercent >= 80) StatusCritical else CodexGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Progress bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cr = CornerRadius(4.dp.toPx())
            drawRoundRect(color = progressTrackColor, cornerRadius = cr)
            if (animatedProgress > 0f) {
                drawRoundRect(
                    brush = gradient,
                    size = Size(size.width * animatedProgress, size.height),
                    cornerRadius = cr
                )
            }
        }
    }

    // Reset time
    if (resetAt != null) {
        val remaining = Duration.between(Instant.now(), resetAt)
        if (!remaining.isNegative) {
            val totalSeconds = remaining.seconds
            val days = totalSeconds / 86400
            val h = (totalSeconds % 86400) / 3600
            val m = (totalSeconds % 3600) / 60
            val resetText = when {
                days > 0 -> "Resets in ${days}d ${h}h"
                h > 0 -> "Resets in ${h}h ${m}m"
                m > 0 -> "Resets in ${m}m"
                else -> "Resetting soon..."
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = resetText,
                color = ExtendedTheme.colors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isAuthError: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAuthError) "Session Expired" else "Error",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = StatusCritical
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = ExtendedTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = ClaudePurple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isAuthError) "Sign In Again" else "Retry",
                fontSize = 15.sp
            )
        }
    }
}
