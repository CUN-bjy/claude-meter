package com.claudeusage.widget.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.BuildConfig
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.ui.components.BannerAd
import com.claudeusage.widget.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val NICKNAME_MAX_LENGTH = 30
private const val DONATE_URL = "https://paypal.me/JunyeobBaek"
private val APP_VERSION = BuildConfig.VERSION_NAME

data class MetricToggle(
    val key: String,
    val label: String,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notificationEnabled: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    coachEnabled: Boolean = true,
    onCoachToggle: (Boolean) -> Unit = {},
    metricToggles: List<MetricToggle>,
    onMetricToggle: (String, Boolean) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    primaryMode: String = AppPreferences.MODE_CLAUDE,
    onPrimaryModeChange: (String) -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    claudeAccounts: AccountList = AccountList(),
    onSwitchClaudeAccount: (String) -> Unit = {},
    onAddClaudeAccount: () -> Unit = {},
    onRemoveClaudeAccount: (String) -> Unit = {},
    onRenameClaudeAccount: (String, String) -> Unit = { _, _ -> },
    codexAccounts: AccountList = AccountList(),
    onSwitchCodexAccount: (String) -> Unit = {},
    onAddCodexAccount: () -> Unit = {},
    onRemoveCodexAccount: (String) -> Unit = {},
    onRenameCodexAccount: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val swipeThreshold = screenWidthPx * 0.3f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value > swipeThreshold) {
                                offsetX.animateTo(screenWidthPx, tween(200))
                                onBack()
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, tween(200))
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            val newValue = (offsetX.value + dragAmount).coerceAtLeast(0f)
                            offsetX.snapTo(newValue)
                        }
                    }
                )
            }
    ) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = ExtendedTheme.colors.textSecondary
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Accounts section
            SectionLabel("Accounts")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    AccountGroup(
                        title = "Claude",
                        accounts = claudeAccounts,
                        accentColor = ClaudePurpleLight,
                        addLabel = "Add Claude account",
                        onSwitch = onSwitchClaudeAccount,
                        onAdd = onAddClaudeAccount,
                        onRemove = onRemoveClaudeAccount,
                        onRename = onRenameClaudeAccount
                    )
                    SettingsDivider()
                    AccountGroup(
                        title = "ChatGPT",
                        accounts = codexAccounts,
                        accentColor = CodexGreen,
                        addLabel = "Add ChatGPT account",
                        onSwitch = onSwitchCodexAccount,
                        onAdd = onAddCodexAccount,
                        onRemove = onRemoveCodexAccount,
                        onRename = onRenameCodexAccount
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mode section
            SectionLabel("Mode")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    ) {
                        val modes = listOf(
                            AppPreferences.MODE_CLAUDE to "Claude",
                            AppPreferences.MODE_CHATGPT to "ChatGPT"
                        )
                        modes.forEachIndexed { index, (key, label) ->
                            val accent = if (key == AppPreferences.MODE_CHATGPT) CodexGreen else ClaudePurple
                            SegmentedButton(
                                selected = primaryMode == key,
                                onClick = { onPrimaryModeChange(key) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = accent,
                                    activeContentColor = Color.White,
                                    inactiveContainerColor = Color.Transparent,
                                    inactiveContentColor = ExtendedTheme.colors.textSecondary
                                )
                            ) {
                                Text(text = label, fontSize = 13.sp)
                            }
                        }
                    }
                    Text(
                        text = if (primaryMode == AppPreferences.MODE_CHATGPT) {
                            "ChatGPT/Codex usage leads the main screen, widget and notification. Claude is shown below it."
                        } else {
                            "Claude usage leads the main screen, widget and notification. ChatGPT/Codex is shown below it."
                        },
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notification section
            SectionLabel("Notification")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    SettingsToggleRow(
                        title = "Persistent Notification",
                        subtitle = "Show usage in the notification bar",
                        checked = notificationEnabled,
                        onCheckedChange = onNotificationToggle
                    )
                    Divider(
                        color = ExtendedTheme.colors.dividerColor,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SettingsToggleRow(
                        title = "Productivity Coach",
                        subtitle = "Smart usage tips & reset alerts",
                        checked = coachEnabled,
                        onCheckedChange = onCoachToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme section
            SectionLabel("Theme")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    val options = listOf("dark" to "Dark", "light" to "Light", "system" to "System")
                    options.forEachIndexed { index, (key, label) ->
                        SegmentedButton(
                            selected = themeMode == key,
                            onClick = { onThemeModeChange(key) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = ClaudePurple,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = ExtendedTheme.colors.textSecondary
                            )
                        ) {
                            Text(text = label, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display section
            SectionLabel("Display")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    metricToggles.forEachIndexed { index, toggle ->
                        SettingsToggleRow(
                            title = toggle.label,
                            checked = toggle.enabled,
                            onCheckedChange = { onMetricToggle(toggle.key, it) }
                        )
                        if (index < metricToggles.lastIndex) {
                            Divider(
                                color = ExtendedTheme.colors.dividerColor,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Banner Ad
            BannerAd(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))

            // About section
            SectionLabel("About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    SettingsInfoRow(title = "Version", value = APP_VERSION)
                    SettingsDivider()
                    SettingsLinkRow(title = "Privacy Policy") {
                        onPrivacyPolicyClick()
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Buy Me a Coffee badge
            Button(
                onClick = { uriHandler.openUri(DONATE_URL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFDD00)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "\u2615  Buy me a coffee",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer
            Text(
                text = "This app is not affiliated with or endorsed by Anthropic.",
                color = ExtendedTheme.colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    } // Box

}

/**
 * One service's saved logins: tap a row to switch to it, the cross removes it
 * (after a confirmation), and the last row logs in to another account.
 */
@Composable
private fun AccountGroup(
    title: String,
    accounts: AccountList,
    accentColor: Color,
    addLabel: String,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<AccountSummary?>(null) }
    var renaming by remember { mutableStateOf<AccountSummary?>(null) }

    renaming?.let { account ->
        var name by remember(account.id) { mutableStateOf(account.nickname) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Nickname", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (account.label.isNotBlank()) {
                        Text(
                            text = account.label,
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(NICKNAME_MAX_LENGTH) },
                        placeholder = { Text("e.g. Work, Personal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kept on this device for this email, even if you log out. " +
                            "Leave empty to remove.",
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = null
                    onRename(account.id, name)
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingRemoval?.let { account ->
        val label = accounts.labelOf(account)
        val isActive = account.id == accounts.activeId
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove account", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    buildString {
                        append("Remove $label from Claude Meter?")
                        if (isActive && accounts.accounts.size > 1) {
                            append(" You'll switch to your next saved account.")
                        }
                        append(" You can log in again anytime to add it back.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    onRemove(account.id)
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)
        )
        accounts.accounts.forEach { account ->
            val isActive = account.id == accounts.activeId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isActive) { onSwitch(account.id) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (isActive) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active account",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accounts.labelOf(account),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            accounts.detailOf(account),
                            if (isActive) "In use" else "Tap to switch"
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ExtendedTheme.colors.textMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { renaming = account }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename ${accounts.labelOf(account)}",
                        tint = ExtendedTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { pendingRemoval = account }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${accounts.labelOf(account)}",
                        tint = ExtendedTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = addLabel,
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = ExtendedTheme.colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = ExtendedTheme.colors.dividerColor,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = ExtendedTheme.colors.textMuted,
                    fontSize = 13.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ClaudePurple,
                checkedTrackColor = ClaudePurpleDark
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = ExtendedTheme.colors.textMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "\u203A",
            color = ExtendedTheme.colors.textMuted,
            fontSize = 18.sp
        )
    }
}

