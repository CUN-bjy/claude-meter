package com.claudeusage.widget.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudeusage.widget.data.model.Account
import com.claudeusage.widget.data.model.CodexCredentials

class CodexCredentialManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "codex_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val store = AccountStore(
        prefs,
        encode = { credentials: CodexCredentials, obj ->
            obj.put(KEY_ACCESS_TOKEN, credentials.accessToken)
            obj.put(KEY_SESSION_COOKIES, credentials.sessionCookies)
        },
        decode = { obj ->
            CodexCredentials(obj.optString(KEY_ACCESS_TOKEN), obj.optString(KEY_SESSION_COOKIES))
                .takeIf { it.isValid }
        }
    ).also { store ->
        store.migrateLegacy(
            legacy = {
                val token = prefs.getString(KEY_ACCESS_TOKEN, null)
                val cookies = prefs.getString(KEY_SESSION_COOKIES, null)
                if (token != null && cookies != null) CodexCredentials(token, cookies) else null
            },
            clearLegacy = { remove(KEY_ACCESS_TOKEN).remove(KEY_SESSION_COOKIES) }
        )
    }

    /** Credentials of the active account. */
    fun getCredentials(): CodexCredentials? = store.getActiveAccount()?.credentials

    fun getAccounts(): List<Account<CodexCredentials>> = store.getAccounts()

    val activeAccountId: String?
        get() = store.activeAccountId

    /** Saves a login and switches to it. */
    fun saveCredentials(credentials: CodexCredentials, label: String = ""): Account<CodexCredentials> =
        store.upsert(label, credentials) { it.accessToken == credentials.accessToken }

    fun switchAccount(id: String): Boolean = store.setActive(id)

    fun setAccountLabel(id: String, label: String) = store.setLabel(id, label)

    fun setAccountNickname(id: String, nickname: String) = store.setNickname(id, nickname)

    fun updateCredentials(id: String, credentials: CodexCredentials) =
        store.updateCredentials(id, credentials)

    fun removeAccount(id: String) = store.remove(id)


    fun hasCredentials(): Boolean {
        return getCredentials()?.isValid == true
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SESSION_COOKIES = "session_cookies"
    }
}
