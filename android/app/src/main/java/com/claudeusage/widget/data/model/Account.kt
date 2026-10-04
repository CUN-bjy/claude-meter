package com.claudeusage.widget.data.model

/**
 * One saved login. [label] is the account email when the server reports
 * one, [nickname] the name the user gave it (empty if none), and
 * [credentials] either [Credentials] (Claude) or [CodexCredentials] (ChatGPT).
 */
data class Account<T>(
    val id: String,
    val label: String,
    val credentials: T,
    val nickname: String = ""
)
