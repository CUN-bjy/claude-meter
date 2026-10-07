package com.claudeusage.widget.data.model

enum class CoachSeverity {
    WARNING, CRITICAL, POSITIVE, INFO
}

data class CoachNotification(
    val message: String,
    val severity: CoachSeverity
)
