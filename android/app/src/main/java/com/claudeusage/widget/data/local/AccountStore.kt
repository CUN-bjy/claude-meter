package com.claudeusage.widget.data.local

import android.content.SharedPreferences
import com.claudeusage.widget.data.model.Account
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Keeps a list of saved accounts plus which one is active, as JSON in an
 * (encrypted) [SharedPreferences]. Everything that only needs "the current
 * login" (widget, worker, notification) keeps reading the active account.
 */
internal class AccountStore<T>(
    private val prefs: SharedPreferences,
    private val encode: (T, JSONObject) -> Unit,
    private val decode: (JSONObject) -> T?
) {

    fun getAccounts(): List<Account<T>> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val names = nicknames
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val credentials = decode(obj) ?: return@mapNotNull null
                val label = obj.optString(FIELD_LABEL)
                Account(id, label, credentials, names[nicknameKey(id, label)].orEmpty())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val activeAccountId: String?
        get() {
            val accounts = getAccounts()
            val id = prefs.getString(KEY_ACTIVE_ID, null)
            return accounts.firstOrNull { it.id == id }?.id ?: accounts.firstOrNull()?.id
        }

    fun getActiveAccount(): Account<T>? {
        val id = activeAccountId ?: return null
        return getAccounts().firstOrNull { it.id == id }
    }

    /**
     * Adds a login and makes it active. Logging in again to an account
     * that is already saved (same [label], or same [sameAs] match)
     * refreshes its credentials instead of adding a duplicate.
     */
    fun upsert(label: String, credentials: T, sameAs: (T) -> Boolean): Account<T> {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst {
            (label.isNotBlank() && it.label == label) || sameAs(it.credentials)
        }
        val account = if (index >= 0) {
            val existing = accounts[index]
            existing.copy(label = label.ifBlank { existing.label }, credentials = credentials)
                .also { accounts[index] = it }
        } else {
            Account(UUID.randomUUID().toString(), label, credentials).also { accounts.add(it) }
        }
        save(accounts, account.id)
        return account
    }

    /** Replaces an account's credentials in place, e.g. after a token refresh. */
    fun updateCredentials(id: String, credentials: T) {
        val accounts = getAccounts().map { if (it.id == id) it.copy(credentials = credentials) else it }
        save(accounts, prefs.getString(KEY_ACTIVE_ID, null))
    }

    fun setLabel(id: String, label: String) {
        val accounts = getAccounts().map { if (it.id == id) it.copy(label = label) else it }
        save(accounts, prefs.getString(KEY_ACTIVE_ID, null))
        // A nickname given before the email was known moves onto the email
        val byId = nicknames[nicknameKey(id, "")]
        if (byId != null && label.isNotBlank()) {
            saveNicknames(nicknames - nicknameKey(id, "") + (nicknameKey(id, label) to byId))
        }
    }

    /**
     * Names the account. Nicknames are kept by email, apart from the account
     * list, so they survive a logout or expired session: logging in to the
     * same email again brings the nickname back. A blank name forgets it.
     */
    fun setNickname(id: String, nickname: String) {
        val account = getAccounts().firstOrNull { it.id == id } ?: return
        val key = nicknameKey(account.id, account.label)
        val trimmed = nickname.trim()
        saveNicknames(if (trimmed.isEmpty()) nicknames - key else nicknames + (key to trimmed))
    }

    private val nicknames: Map<String, String>
        get() {
            val json = prefs.getString(KEY_NICKNAMES, null) ?: return emptyMap()
            return try {
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.optString(it) }
            } catch (e: Exception) {
                emptyMap()
            }
        }

    private fun saveNicknames(map: Map<String, String>) {
        prefs.edit().putString(KEY_NICKNAMES, JSONObject(map).toString()).apply()
    }

    /** Emails compare case-insensitively; an account without one is keyed by its id. */
    private fun nicknameKey(id: String, label: String): String =
        label.trim().lowercase().ifEmpty { "id:$id" }

    fun setActive(id: String): Boolean {
        if (getAccounts().none { it.id == id }) return false
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
        return true
    }

    /** Removes an account; if it was active, the first remaining one becomes active. */
    fun remove(id: String) {
        val remaining = getAccounts().filter { it.id != id }
        val active = prefs.getString(KEY_ACTIVE_ID, null)
            ?.takeIf { it != id && remaining.any { a -> a.id == it } }
            ?: remaining.firstOrNull()?.id
        save(remaining, active)
    }

    /** Moves a pre-multi-account single login into the list, once. */
    fun migrateLegacy(legacy: () -> T?, clearLegacy: SharedPreferences.Editor.() -> Unit) {
        // The app, widget and worker may all open the store at once; only
        // one of them may read the legacy login before it is cleared.
        synchronized(MIGRATION_LOCK) {
            if (prefs.contains(KEY_ACCOUNTS)) return
            val credentials = legacy()
            val accounts: List<Account<T>> = if (credentials != null) {
                listOf(Account(LEGACY_ACCOUNT_ID, "", credentials))
            } else {
                emptyList()
            }
            save(accounts, accounts.firstOrNull()?.id)
            prefs.edit().apply(clearLegacy).apply()
        }
    }

    private fun save(accounts: List<Account<T>>, activeId: String?) {
        val array = JSONArray()
        for (account in accounts) {
            val obj = JSONObject()
                .put(FIELD_ID, account.id)
                .put(FIELD_LABEL, account.label)
            encode(account.credentials, obj)
            array.put(obj)
        }
        prefs.edit()
            .putString(KEY_ACCOUNTS, array.toString())
            .apply { if (activeId != null) putString(KEY_ACTIVE_ID, activeId) else remove(KEY_ACTIVE_ID) }
            .apply()
    }

    companion object {
        /**
         * Id given to the login carried over from a single-account install,
         * so its existing usage history (stored before accounts had ids)
         * stays attached to it.
         */
        const val LEGACY_ACCOUNT_ID = "default"

        private val MIGRATION_LOCK = Any()
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE_ID = "active_account_id"
        private const val KEY_NICKNAMES = "nicknames"
        private const val FIELD_ID = "id"
        private const val FIELD_LABEL = "label"
    }
}
