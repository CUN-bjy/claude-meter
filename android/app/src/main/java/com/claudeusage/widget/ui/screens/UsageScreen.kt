package com.claudeusage.widget.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.R
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
    primaryMode: String = AppPreferences.MODE_CLAUDE,
    onModeChange: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLoginClick: () -> Unit,
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
    val isChatGptMode = primaryMode == AppPreferences.MODE_CHATGPT
    // The top bar acts on whichever provider the current mode is centered on
    val primaryConnected = if (isChatGptMode) {
        codexState is CodexUiState.Connected
    } else {
        uiState is UiState.Success
    }
    // The account menu under the title lists the primary provider's accounts
    val headerMenuOpen = remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        val activeLabel = claudeAccounts.active?.let { claudeAccounts.labelOf(it) }
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = if (isChatGptMode) "Disconnect ChatGPT" else "Logout",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isChatGptMode) {
                        val codexLabel = codexAccounts.active?.let { codexAccounts.labelOf(it) }
                        if (codexLabel != null && codexAccounts.accounts.size > 1) {
                            "Disconnect $codexLabel? You'll switch to your next saved ChatGPT account."
                        } else {
                            "Are you sure you want to disconnect your ChatGPT account?"
                        }
                    } else if (activeLabel != null && claudeAccounts.accounts.size > 1) {
                        "Log out of $activeLabel? You'll switch to your next saved account."
                    } else {
                        "Are you sure you want to logout?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    if (isChatGptMode) onCodexLogout() else onLogout()
                }) {
                    Text(
                        text = if (isChatGptMode) "Disconnect" else "Logout",
                        color = MaterialTheme.colorScheme.error
                    )
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
                    val titleText = if (isChatGptMode) "Codex Meter" else "Claude Meter"
                    val headerAccounts = if (isChatGptMode) codexAccounts else claudeAccounts
                    if (headerAccounts.accounts.isEmpty()) {
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        Column {
                            Text(
                                text = titleText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            // Email + arrow, like the Codex card, so it reads as an account menu
                            AccountSwitcher(
                                accounts = headerAccounts,
                                accentColor = if (isChatGptMode) CodexGreen else ClaudePurpleLight,
                                addLabel = if (isChatGptMode) "Add ChatGPT account" else "Add Claude account",
                                onSwitch = if (isChatGptMode) onSwitchCodexAccount else onSwitchAccount,
                                onAdd = if (isChatGptMode) onCodexLoginClick else onLoginClick,
                                expandedState = headerMenuOpen,
                                onOpen = onDismissAccountHint
                            ) {
                                Text(
                                    text = headerAccounts.active?.let { headerAccounts.labelOf(it) }
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
                    // Forecast is built from Claude history only
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
                    // Refresh and logout act on the primary provider, so they stay
                    // available when only that provider is connected
                    if (uiState is UiState.Success || primaryConnected) {
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isChatGptMode) CodexGreen else ClaudePurpleLight
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = ExtendedTheme.colors.textSecondary
                                )
                            }
                        }
                        if (primaryConnected) {
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = if (isChatGptMode) "Disconnect" else "Logout",
                                    tint = ExtendedTheme.colors.textSecondary
                                )
                            }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                // The full-screen login and splash only apply when neither
                // provider is connected; otherwise whichever one is missing
                // is just a connect card inside the normal layout.
                val nothingConnected = uiState is UiState.LoginRequired &&
                    codexState is CodexUiState.NotConnected
                val coldStart = uiState is UiState.Loading &&
                    codexState is CodexUiState.NotConnected

                when {
                    coldStart -> LoadingContent()
                    nothingConnected -> LoginContent(
                        onLoginClick = onLoginClick,
                        onCodexLoginClick = onCodexLoginClick,
                        onModeChange = onModeChange
                    )
                    isChatGptMode -> ChatGptContent(
                        codexState = codexState,
                        claudeState = uiState,
                        lastUpdated = lastUpdated,
                        hiddenMetrics = hiddenMetrics,
                        onCodexLoginClick = onCodexLoginClick,
                        onLoginClick = onLoginClick,
                        claudeAccounts = claudeAccounts,
                        onSwitchAccount = onSwitchAccount
                    )
                    else -> UsageContent(
                        claudeState = uiState,
                        lastUpdated = lastUpdated,
                        hiddenMetrics = hiddenMetrics,
                        codexState = codexState,
                        onLoginClick = onLoginClick,
                        onRefresh = onRefresh,
                        onCodexLoginClick = onCodexLoginClick,
                        onCodexLogout = onCodexLogout,
                        codexAccounts = codexAccounts,
                        onSwitchCodexAccount = onSwitchCodexAccount,
                        showAccountHint = showAccountHint && claudeAccounts.accounts.isNotEmpty(),
                        onAccountHintClick = {
                            onDismissAccountHint()
                            headerMenuOpen.value = true
                        },
                        onDismissAccountHint = onDismissAccountHint
                    )
                }
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
    onCodexLoginClick: () -> Unit,
    onModeChange: (String) -> Unit
) {
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
            text = "Track your usage limits",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in with the account you use most",
            fontSize = 14.sp,
            color = ExtendedTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Whichever provider the user signs in with becomes the primary one
        ProviderSignInButton(
            label = "Continue with Claude",
            iconRes = R.drawable.ic_provider_claude,
            borderColor = ProviderClay,
            contentColor = ExtendedTheme.colors.providerClayText,
            onClick = {
                onModeChange(AppPreferences.MODE_CLAUDE)
                onLoginClick()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProviderSignInButton(
            label = "Continue with ChatGPT",
            iconRes = R.drawable.ic_provider_openai,
            borderColor = ExtendedTheme.colors.providerNeutralBorder,
            contentColor = ExtendedTheme.colors.providerNeutralText,
            onClick = {
                onModeChange(AppPreferences.MODE_CHATGPT)
                onCodexLoginClick()
            }
        )
    }
}

/**
 * Sign-in choice. The two providers are told apart by their own logo and by a
 * warm/neutral border - Anthropic's clay against a neutral grey - rather than by
 * two saturated fills competing for attention.
 */
@Composable
private fun ProviderSignInButton(
    label: String,
    iconRes: Int,
    borderColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun UsageContent(
    claudeState: UiState,
    lastUpdated: String?,
    hiddenMetrics: Set<String>,
    codexState: CodexUiState = CodexUiState.NotConnected,
    onLoginClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
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

        when (claudeState) {
            is UiState.Success -> {
                val data = claudeState.data
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
            }
            is UiState.Loading -> ProviderLoadingBlock(accent = ClaudePurpleLight)
            is UiState.LoginRequired -> PrimaryConnectCard(
                title = "Claude Usage",
                message = "Sign in to track your Claude usage.",
                buttonText = "Sign in with Claude.ai",
                accent = ClaudePurple,
                onClick = onLoginClick
            )
            is UiState.Error -> PrimaryConnectCard(
                title = "Claude Usage",
                message = claudeState.message,
                buttonText = if (claudeState.isAuthError) "Sign In Again" else "Retry",
                accent = ClaudePurple,
                onClick = if (claudeState.isAuthError) onLoginClick else onRefresh,
                isError = true
            )
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
                    Text(
                        text = "Codex Usage",
                        color = CodexGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        Column {
                            Text(
                                text = accounts.labelOf(account),
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            accounts.detailOf(account)?.let {
                                Text(
                                    text = it,
                                    color = ExtendedTheme.colors.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
    val remaining = resetAt?.let { Duration.between(Instant.now(), it) }
    CompactUsageRow(
        label = label,
        usedPercent = usedPercent,
        remaining = remaining,
        accent = CodexGreen,
        accentLight = CodexGreenLight,
        progressTrackColor = progressTrackColor
    )
}

/**
 * One compact "label — percent — bar — resets in" row, used for whichever
 * provider is currently the secondary one (and for the Codex windows).
 */
@Composable
private fun CompactUsageRow(
    label: String,
    usedPercent: Double,
    remaining: Duration?,
    accent: Color,
    accentLight: Color,
    progressTrackColor: Color
) {
    val gradient = Brush.horizontalGradient(colors = listOf(accent, accentLight))
    val animatedProgress by animateFloatAsState(
        targetValue = (usedPercent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "compact_progress_$label"
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
            color = if (usedPercent >= 80) StatusCritical else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

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

    val resetText = remaining?.let { formatResetText(it) }
    if (resetText != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = resetText,
            color = ExtendedTheme.colors.textMuted,
            fontSize = 10.sp
        )
    }
}

private fun formatResetText(remaining: Duration): String? {
    if (remaining.isNegative) return null
    val totalSeconds = remaining.seconds
    val days = totalSeconds / 86400
    val h = (totalSeconds % 86400) / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "Resets in ${days}d ${h}h"
        h > 0 -> "Resets in ${h}h ${m}m"
        m > 0 -> "Resets in ${m}m"
        else -> "Resetting soon..."
    }
}

/** ChatGPT-centric layout: Codex on top, Claude demoted to a compact card. */
@Composable
private fun ChatGptContent(
    codexState: CodexUiState,
    claudeState: UiState,
    lastUpdated: String?,
    hiddenMetrics: Set<String>,
    onCodexLoginClick: () -> Unit,
    onLoginClick: () -> Unit,
    claudeAccounts: AccountList = AccountList(),
    onSwitchAccount: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        when (codexState) {
            is CodexUiState.Connected -> {
                val data = codexState.data

                if (data.limitReached) {
                    LimitBanner(text = "Rate limit reached")
                    Spacer(modifier = Modifier.height(12.dp))
                }

                CodexPrimaryCard(
                    title = "Current Session",
                    subtitle = "5-hour window",
                    window = data.primaryWindow
                )
                Spacer(modifier = Modifier.height(12.dp))

                CodexPrimaryCard(
                    title = "Weekly Limit",
                    subtitle = "7-day window",
                    window = data.secondaryWindow
                )

                if (data.planType != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Plan: ${data.planType}",
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            is CodexUiState.Loading -> ProviderLoadingBlock(accent = CodexGreen)
            is CodexUiState.NotConnected -> PrimaryConnectCard(
                title = "Codex Usage",
                message = "Connect your ChatGPT account to track Codex usage.",
                buttonText = "Sign in with ChatGPT",
                accent = CodexGreen,
                onClick = onCodexLoginClick
            )
            is CodexUiState.Error -> PrimaryConnectCard(
                title = "Codex Usage",
                message = codexState.message,
                buttonText = if (codexState.isAuthError) "Reconnect" else "Retry",
                accent = CodexGreen,
                onClick = onCodexLoginClick,
                isError = true
            )
        }

        // Claude, demoted to the secondary slot
        if (AppPreferences.CLAUDE_METRIC_KEY !in hiddenMetrics) {
            Spacer(modifier = Modifier.height(20.dp))
            ClaudeSecondaryCard(
                claudeState = claudeState,
                onLoginClick = onLoginClick,
                claudeAccounts = claudeAccounts,
                onSwitchAccount = onSwitchAccount
            )
        }

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

        Spacer(modifier = Modifier.height(16.dp))
        BannerAd(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LimitBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusCritical.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = StatusCritical,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Codex equivalent of [UsageCard] - the headline card in ChatGPT mode. */
@Composable
private fun CodexPrimaryCard(
    title: String,
    subtitle: String,
    window: CodexUsageData.UsageWindow?
) {
    val usedPercent = window?.usedPercent ?: 0.0
    val animatedProgress by animateFloatAsState(
        targetValue = (usedPercent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "codex_primary_$title"
    )
    val accent = when {
        usedPercent >= 90.0 -> StatusCritical
        usedPercent >= 75.0 -> StatusWarning
        else -> CodexGreen
    }
    val gradient = when {
        usedPercent >= 90.0 -> Brush.horizontalGradient(
            listOf(Color(0xFFCC3030), StatusCritical, Color(0xFFF07070))
        )
        usedPercent >= 75.0 -> Brush.horizontalGradient(
            listOf(Color(0xFFCC7A20), StatusWarning, Color(0xFFF0B060))
        )
        else -> Brush.horizontalGradient(
            listOf(CodexGreenDark, CodexGreen, CodexGreenLight)
        )
    }
    val progressTrackColor = ExtendedTheme.colors.progressTrack

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
                Text(
                    text = "${String.format("%.1f", usedPercent)}%",
                    color = accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cr = CornerRadius(6.dp.toPx())
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

            val resetText = window?.resetAt
                ?.let { Duration.between(Instant.now(), it) }
                ?.let { formatResetText(it) }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = resetText ?: "No active window",
                color = ExtendedTheme.colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

/** Connect prompt in the primary slot, for whichever provider is missing. */
@Composable
private fun PrimaryConnectCard(
    title: String,
    message: String,
    buttonText: String,
    accent: Color,
    onClick: () -> Unit,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = if (isError) StatusCritical else ExtendedTheme.colors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = buttonText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProviderLoadingBlock(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = accent
        )
    }
}

/** Claude in the secondary slot, mirroring how Codex looks in Claude mode. */
@Composable
private fun ClaudeSecondaryCard(
    claudeState: UiState,
    onLoginClick: () -> Unit,
    claudeAccounts: AccountList = AccountList(),
    onSwitchAccount: (String) -> Unit = {}
) {
    val progressTrackColor = ExtendedTheme.colors.progressTrack

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column {
                Text(
                    text = "Claude Usage",
                    color = ClaudePurpleLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val activeClaude = claudeAccounts.active
                if (activeClaude == null) {
                    Text(
                        text = "Claude.ai",
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                } else {
                    AccountSwitcher(
                        accounts = claudeAccounts,
                        accentColor = ClaudePurpleLight,
                        addLabel = "Add Claude account",
                        onSwitch = onSwitchAccount,
                        onAdd = onLoginClick
                    ) {
                        Text(
                            text = claudeAccounts.labelOf(activeClaude),
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (claudeState) {
                is UiState.Success -> {
                    CompactUsageRow(
                        label = "Session (5h)",
                        usedPercent = claudeState.data.fiveHour?.utilization ?: 0.0,
                        remaining = claudeState.data.fiveHour?.remainingDuration,
                        accent = ClaudePurpleLight,
                        accentLight = ClaudePurple,
                        progressTrackColor = progressTrackColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CompactUsageRow(
                        label = "Weekly (7d)",
                        usedPercent = claudeState.data.sevenDay?.utilization ?: 0.0,
                        remaining = claudeState.data.sevenDay?.remainingDuration,
                        accent = ClaudePurpleLight,
                        accentLight = ClaudePurple,
                        progressTrackColor = progressTrackColor
                    )
                }
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = ClaudePurpleLight
                    )
                }
                is UiState.LoginRequired -> ClaudeConnectButton(
                    text = "Sign in with Claude.ai",
                    onClick = onLoginClick
                )
                is UiState.Error -> {
                    Text(
                        text = claudeState.message,
                        color = StatusCritical,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ClaudeConnectButton(
                        text = if (claudeState.isAuthError) "Sign In Again" else "Retry",
                        onClick = onLoginClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeConnectButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ClaudePurpleLight),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}
