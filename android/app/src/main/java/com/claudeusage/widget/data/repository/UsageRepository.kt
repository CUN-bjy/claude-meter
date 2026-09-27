package com.claudeusage.widget.data.repository

import com.claudeusage.widget.data.model.Credentials
import com.claudeusage.widget.data.model.ExtraUsageInfo
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.UsageMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class UsageRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchUsageData(credentials: Credentials): Result<UsageData> =
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val usageDeferred = async {
                        fetchJson(
                            "$BASE_URL/api/organizations/${credentials.organizationId}/usage",
                            credentials.sessionKey
                        )
                    }
                    val overageDeferred = async {
                        runCatching {
                            fetchJson(
                                "$BASE_URL/api/organizations/${credentials.organizationId}/overage_spend_limit",
                                credentials.sessionKey
                            )
                        }.getOrNull()
                    }
                    val prepaidDeferred = async {
                        runCatching {
                            fetchJson(
                                "$BASE_URL/api/organizations/${credentials.organizationId}/prepaid/credits",
                                credentials.sessionKey
                            )
                        }.getOrNull()
                    }

                    val usageResult = usageDeferred.await()
                    val overageJson = overageDeferred.await()
                    val prepaidJson = prepaidDeferred.await()

                    val usageData = UsageData.fromJson(usageResult)
                    android.util.Log.d(
                        TAG,
                        "usage response keys=${usageData.rawKeys} parsed=" +
                            usageData.dynamicMetrics.joinToString {
                                "${it.key}→'${it.label}'(${it.metric.utilization}%)"
                            }
                    )

                    // Merge overage spending data
                    var extraMetric = usageData.extraUsage
                    var extraInfo: ExtraUsageInfo? = null

                    if (overageJson != null) {
                        val limit = overageJson.optIntOrNull("monthly_credit_limit")
                            ?: overageJson.optIntOrNull("spend_limit_amount_cents")
                        val used = overageJson.optIntOrNull("used_credits")
                            ?: overageJson.optIntOrNull("balance_cents")
                        val enabled = if (overageJson.has("is_enabled"))
                            overageJson.optBoolean("is_enabled") else (limit != null)

                        if (enabled && limit != null && limit > 0 && used != null) {
                            val utilization = (used.toDouble() / limit) * 100
                            extraMetric = UsageMetric(utilization, null)
                            extraInfo = ExtraUsageInfo(usedCents = used, limitCents = limit)
                        }
                    }

                    // Merge prepaid balance
                    if (prepaidJson != null) {
                        val amount = prepaidJson.optIntOrNull("amount")
                        if (amount != null) {
                            extraInfo = (extraInfo ?: ExtraUsageInfo()).copy(balanceCents = amount)
                        }
                    }

                    Result.success(
                        usageData.copy(
                            extraUsage = extraMetric ?: usageData.extraUsage,
                            extraUsageInfo = extraInfo
                        )
                    )
                }
            } catch (e: AuthException) {
                Result.failure(e)
            } catch (e: CloudflareException) {
                Result.failure(e)
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun fetchJson(url: String, sessionKey: String): JSONObject {
        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                delay(backoffMs)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", "sessionKey=$sessionKey")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", BASE_URL)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            when {
                isCloudflareChallenge(response, body) -> {
                    throw CloudflareException(CLOUDFLARE_MESSAGE)
                }
                isAuthFailure(response.code, body) -> {
                    throw AuthException("[HTTP ${response.code}] Session expired.\nPlease log in again.")
                }
                response.code == 429 -> {
                    lastException = RateLimitException(
                        "[HTTP 429] Too many requests.\nPlease wait a moment and try again."
                    )
                    continue // retry with backoff
                }
                !response.isSuccessful -> {
                    throw IOException("[HTTP ${response.code}] ${response.message.ifEmpty { "Request failed." }}")
                }
                body.trimStart().startsWith("<") -> {
                    throw IOException("[HTTP ${response.code}] Unexpected HTML response from server.")
                }
            }

            return JSONObject(body)
        }

        throw lastException ?: IOException("Request failed after retries.")
    }

    suspend fun fetchOrganizationId(sessionKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/api/organizations")
                    .addHeader("Cookie", "sessionKey=$sessionKey")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Referer", BASE_URL)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                when {
                    isCloudflareChallenge(response, body) -> {
                        Result.failure(CloudflareException(CLOUDFLARE_MESSAGE))
                    }
                    isAuthFailure(response.code, body) -> {
                        Result.failure(AuthException("[HTTP ${response.code}] Invalid session key."))
                    }
                    !response.isSuccessful -> {
                        Result.failure(IOException("[HTTP ${response.code}] ${response.message.ifEmpty { "Request failed." }}"))
                    }
                    else -> {
                        val jsonArray = JSONArray(body)
                        if (jsonArray.length() > 0) {
                            val orgId = jsonArray.getJSONObject(0).getString("uuid")
                            Result.success(orgId)
                        } else {
                            Result.failure(IOException("No organizations found."))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Best-effort label for the account switcher: the login email, or the
     * organization name when the account endpoint doesn't report one.
     */
    suspend fun fetchAccountLabel(sessionKey: String): String? =
        withContext(Dispatchers.IO) {
            val email = runCatching {
                fetchJson("$BASE_URL/api/account", sessionKey)
                    .optString("email_address")
                    .takeIf { it.isNotBlank() }
            }.getOrNull()
            email ?: runCatching {
                val request = Request.Builder()
                    .url("$BASE_URL/api/organizations")
                    .addHeader("Cookie", "sessionKey=$sessionKey")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Referer", BASE_URL)
                    .build()
                val body = client.newCall(request).execute().use { it.body?.string() }.orEmpty()
                JSONArray(body).getJSONObject(0).optString("name").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    fun cancelPendingRequests() {
        client.dispatcher.cancelAll()
    }

    suspend fun validateSession(credentials: Credentials): Boolean =
        withContext(Dispatchers.IO) {
            fetchUsageData(credentials).isSuccess
        }

    companion object {
        private const val TAG = "UsageRepository"
        const val BASE_URL = "https://claude.ai"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 2000L
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

/**
 * Cloudflare's bot check answers with an HTML challenge page (usually a 403),
 * which says nothing about the login. It must be recognised before the status
 * code, or a valid saved account gets deleted as "expired".
 */
internal fun isCloudflareChallenge(response: Response, body: String): Boolean =
    response.header("cf-mitigated") != null ||
        body.contains("Just a moment") ||
        body.contains("Enable JavaScript") ||
        body.contains("challenge-platform")

/**
 * A 401 always means the login is gone. A 403 only does when the API itself
 * answered: an HTML 403 is a block page from something in front of it.
 */
internal fun isAuthFailure(code: Int, body: String): Boolean =
    code == 401 || (code == 403 && !body.trimStart().startsWith("<"))

internal const val CLOUDFLARE_MESSAGE =
    "[Cloudflare] The server is blocking automated requests right now.\n" +
        "Your login is still saved. Wait a few minutes, then tap Retry."

class AuthException(message: String) : Exception(message)
class CloudflareException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)
