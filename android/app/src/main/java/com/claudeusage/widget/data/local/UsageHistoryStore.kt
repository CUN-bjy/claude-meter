package com.claudeusage.widget.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UsageHistoryEntry(
    val timestamp: Long,
    val utilization: Double
)

/**
 * Stores utilization history as multiple named series, e.g.
 * "seven_day" (overall Claude weekly), "seven_day_fable",
 * "codex_weekly", so each 7d limit can be graphed independently.
 *
 * Each series belongs to one saved account: it is stored under
 * "<accountId>|<series>" so switching accounts never mixes their graphs.
 */
class UsageHistoryStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * History of the given Claude and Codex accounts, keyed by plain series
     * name ("seven_day", "codex_weekly", ...). Codex series come from
     * [codexAccountId], every other series from [claudeAccountId].
     */
    fun getHistory(
        claudeAccountId: String?,
        codexAccountId: String?
    ): Map<String, List<UsageHistoryEntry>> {
        val result = mutableMapOf<String, List<UsageHistoryEntry>>()
        for ((key, entries) in getAllSeries()) {
            val (accountId, series) = splitKey(key) ?: continue
            val owner = if (series == SERIES_CODEX_WEEKLY) codexAccountId else claudeAccountId
            if (owner != null && accountId == owner) result[series] = entries
        }
        return result
    }

    /** Appends one data point to each of [accountId]'s given series at the same timestamp. */
    fun addEntries(accountId: String, timestamp: Long, values: Map<String, Double>) {
        if (values.isEmpty()) return
        val all = getAllSeries().toMutableMap()
        for ((series, utilization) in values) {
            val key = scopedKey(accountId, series)
            all[key] = (all[key].orEmpty() + UsageHistoryEntry(timestamp, utilization))
                .takeLast(MAX_ENTRIES_PER_SERIES)
        }
        // Drop stale points so abandoned series eventually disappear
        val cutoff = timestamp - MAX_AGE_MS
        val pruned = all
            .mapValues { (_, entries) -> entries.filter { it.timestamp >= cutoff } }
            .filterValues { it.isNotEmpty() }
        saveHistory(pruned)
    }

    /** Removes [accountId]'s series whose names match [predicate], keeping the rest. */
    fun clearSeriesWhere(accountId: String, predicate: (String) -> Boolean) {
        val remaining = getAllSeries().filterKeys { key ->
            val (owner, series) = splitKey(key) ?: return@filterKeys true
            !(owner == accountId && predicate(series))
        }
        saveHistory(remaining)
    }

    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY_V3)
            .remove(KEY_HISTORY_V2)
            .remove(KEY_HISTORY_LEGACY)
            .apply()
    }

    private fun getAllSeries(): Map<String, List<UsageHistoryEntry>> {
        migrateLegacyHistory()
        val json = prefs.getString(KEY_HISTORY_V3, null) ?: return emptyMap()
        return try {
            val root = JSONObject(json)
            root.keys().asSequence().associateWith { key ->
                parseEntries(root.optJSONArray(key))
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Older installs stored a single account's history, either as one
     * array (v1) or as unscoped series (v2). Both belong to the login that
     * the credential managers carry over as [AccountStore.LEGACY_ACCOUNT_ID].
     */
    private fun migrateLegacyHistory() {
        if (prefs.contains(KEY_HISTORY_V3)) return
        val unscoped: Map<String, List<UsageHistoryEntry>> = try {
            val v2 = prefs.getString(KEY_HISTORY_V2, null)
            val v1 = prefs.getString(KEY_HISTORY_LEGACY, null)
            when {
                v2 != null -> {
                    val root = JSONObject(v2)
                    root.keys().asSequence().associateWith { parseEntries(root.optJSONArray(it)) }
                }
                v1 != null -> mapOf(SERIES_WEEKLY_ALL to parseEntries(JSONArray(v1)))
                else -> emptyMap()
            }
        } catch (e: Exception) {
            emptyMap() // Corrupt legacy data — drop it
        }
        saveHistory(
            unscoped
                .filterValues { it.isNotEmpty() }
                .mapKeys { (series, _) -> scopedKey(AccountStore.LEGACY_ACCOUNT_ID, series) }
        )
        prefs.edit().remove(KEY_HISTORY_V2).remove(KEY_HISTORY_LEGACY).apply()
    }

    private fun parseEntries(array: JSONArray?): List<UsageHistoryEntry> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            UsageHistoryEntry(
                timestamp = obj.optLong("t", -1L).takeIf { it >= 0 } ?: return@mapNotNull null,
                utilization = obj.optDouble("u", 0.0)
            )
        }
    }

    private fun saveHistory(series: Map<String, List<UsageHistoryEntry>>) {
        val root = JSONObject()
        for ((key, entries) in series) {
            val array = JSONArray()
            for (entry in entries) {
                array.put(JSONObject().put("t", entry.timestamp).put("u", entry.utilization))
            }
            root.put(key, array)
        }
        prefs.edit().putString(KEY_HISTORY_V3, root.toString()).apply()
    }

    private fun scopedKey(accountId: String, series: String) = "$accountId$SCOPE_SEPARATOR$series"

    private fun splitKey(key: String): Pair<String, String>? {
        val index = key.indexOf(SCOPE_SEPARATOR)
        if (index <= 0) return null
        return key.substring(0, index) to key.substring(index + 1)
    }

    companion object {
        const val SERIES_WEEKLY_ALL = "seven_day"
        const val SERIES_CODEX_WEEKLY = "codex_weekly"

        private const val PREFS_NAME = "claude_usage_history"
        private const val KEY_HISTORY_LEGACY = "history"
        private const val KEY_HISTORY_V2 = "history_v2"
        private const val KEY_HISTORY_V3 = "history_v3"
        private const val SCOPE_SEPARATOR = '|'
        private const val MAX_ENTRIES_PER_SERIES = 2016 // 7 days * 24h * 60min / 5min
        private const val MAX_AGE_MS = 8L * 24 * 60 * 60 * 1000 // 8 days
    }
}
