package com.claudeusage.widget.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudeusage.widget.data.model.Account
import com.claudeusage.widget.data.model.Credentials

class CredentialManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "claude_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val store = AccountStore(
        prefs,
        encode = { credentials: Credentials, obj ->
            obj.put(KEY_SESSION, credentials.sessionKey)
            obj.put(KEY_ORG_ID, credentials.organizationId)
        },
        decode = { obj ->
            Credentials(obj.optString(KEY_SESSION), obj.optString(KEY_ORG_ID))
                .takeIf { it.isValid }
        }
    ).also { store ->
        store.migrateLegacy(
            legacy = {
                val sessionKey = prefs.getString(KEY_SESSION, null)
                val orgId = prefs.getString(KEY_ORG_ID, null)
                if (sessionKey != null && orgId != null) Credentials(sessionKey, orgId) else null
            },
            clearLegacy = { remove(KEY_SESSION).remove(KEY_ORG_ID) }
        )
    }

    /** Credentials of the active account. */
    fun getCredentials(): Credentials? = store.getActiveAccount()?.credentials

    fun getAccounts(): List<Account<Credentials>> = store.getAccounts()

    val activeAccountId: String?
        get() = store.activeAccountId

    /** Saves a login and switches to it. */
    fun saveCredentials(credentials: Credentials, label: String = ""): Account<Credentials> =
        store.upsert(label, credentials) { it.sessionKey == credentials.sessionKey }

    fun switchAccount(id: String): Boolean = store.setActive(id)

    fun setAccountLabel(id: String, label: String) = store.setLabel(id, label)

    fun setAccountNickname(id: String, nickname: String) = store.setNickname(id, nickname)

    fun removeAccount(id: String) = store.remove(id)


    fun hasCredentials(): Boolean {
        return getCredentials()?.isValid == true
    }

    companion object {
        private const val KEY_SESSION = "session_key"
        private const val KEY_ORG_ID = "organization_id"
    }
}
