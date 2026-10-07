package com.claudeusage.widget.data.repository

import com.claudeusage.widget.data.model.CodexCredentials
import com.claudeusage.widget.data.model.CodexUsageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CodexUsageRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchUsageData(credentials: CodexCredentials): Result<CodexUsageData> =
        withContext(Dispatchers.IO) {
            try {
                val json = fetchJson(
                    "$BASE_URL/backend-api/wham/usage",
                    credentials
                )
                Result.success(CodexUsageData.fromJson(json))
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

    private fun fetchJson(url: String, credentials: CodexCredentials): JSONObject {
        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val backoffMs = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                Thread.sleep(backoffMs)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${credentials.accessToken}")
                .addHeader("Cookie", credentials.sessionCookies)
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
                    continue
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

    /** Exchanges ChatGPT session cookies for an access token and the login email. */
    suspend fun fetchSession(cookies: String): Result<CodexSession> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/api/auth/session")
                    .addHeader("Cookie", cookies)
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
                        Result.failure(AuthException("[HTTP ${response.code}] Invalid session."))
                    }
                    !response.isSuccessful -> {
                        Result.failure(IOException("[HTTP ${response.code}] ${response.message.ifEmpty { "Request failed." }}"))
                    }
                    else -> {
                        val json = JSONObject(body)
                        val accessToken = json.optString("accessToken", "")
                        val email = json.optJSONObject("user")?.optString("email").orEmpty()
                        if (accessToken.isNotBlank()) {
                            Result.success(CodexSession(accessToken, email))
                        } else {
                            Result.failure(IOException("No access token in session response."))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun cancelPendingRequests() {
        client.dispatcher.cancelAll()
    }

    companion object {
        const val BASE_URL = "https://chatgpt.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 2000L
    }
}

data class CodexSession(
    val accessToken: String,
    val email: String
)
