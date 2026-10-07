package com.claudeusage.widget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.model.CoachNotification
import com.claudeusage.widget.data.model.CoachSeverity
import com.claudeusage.widget.ui.theme.*

@Composable
fun CoachBanner(
    notification: CoachNotification?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (notification != null) {
            val (bgColor, accentColor, icon) = when (notification.severity) {
                CoachSeverity.CRITICAL -> Triple(
                    StatusCritical.copy(alpha = 0.15f),
                    StatusCritical,
                    "\u26A0\uFE0F"
                )
                CoachSeverity.WARNING -> Triple(
                    StatusWarning.copy(alpha = 0.15f),
                    StatusWarning,
                    "\uD83D\uDCA1"
                )
                CoachSeverity.POSITIVE -> Triple(
                    StatusExtra.copy(alpha = 0.15f),
                    StatusExtra,
                    "\uD83D\uDE80"
                )
                CoachSeverity.INFO -> Triple(
                    ClaudePurple.copy(alpha = 0.15f),
                    ClaudePurpleLight,
                    "\uD83D\uDC64"
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = notification.message,
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
