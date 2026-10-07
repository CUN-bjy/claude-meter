package com.claudeusage.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.claudeusage.widget.ui.theme.*

class LoginActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var cookiePollingRunnable: Runnable? = null
    private var sessionCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeUsageTheme {
                var webViewError by remember { mutableStateOf<String?>(null) }

                if (webViewError != null) {
                    WebViewErrorScreen(
                        message = webViewError!!,
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                } else {
                    LoginWebViewScreen(
                        onSessionCaptured = { sessionKey ->
                            if (!sessionCaptured) {
                                sessionCaptured = true
                                stopCookiePolling()
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_SESSION_KEY, sessionKey)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                        },
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        onError = { error ->
                            webViewError = error
                        },
                        onStartPolling = { callback ->
                            startCookiePolling(callback)
                        }
                    )
                }
            }
        }
    }

    private fun startCookiePolling(onSessionCaptured: (String) -> Unit) {
        stopCookiePolling()
        cookiePollingRunnable = object : Runnable {
            override fun run() {
                if (sessionCaptured) return
                val sessionKey = extractSessionKey()
                if (sessionKey != null) {
                    onSessionCaptured(sessionKey)
                } else {
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.postDelayed(cookiePollingRunnable!!, 2000)
    }

    private fun stopCookiePolling() {
        cookiePollingRunnable?.let { handler.removeCallbacks(it) }
        cookiePollingRunnable = null
    }

    private fun extractSessionKey(): String? {
        return try {
            val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return null
            cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("sessionKey=") }
                ?.substringAfter("sessionKey=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCookiePolling()
    }

    companion object {
        const val EXTRA_SESSION_KEY = "session_key"

        // JS to hide social OAuth buttons (Google, Apple) and the 'or' divider.
        // Neither provider works reliably inside a WebView, so only email
        // login is offered. claude.ai is a SPA: the login form is rendered by
        // React after onPageFinished fires, so a one-shot scan runs too early
        // and misses the buttons. Install a persistent MutationObserver that
        // re-applies the hiding on every DOM change instead.
        internal const val HIDE_SSO_BUTTONS_JS = """
            (function() {
                if (window.__cmHideSsoInstalled) return;
                window.__cmHideSsoInstalled = true;

                var style = document.createElement('style');
                style.textContent =
                    'button[data-testid="google-auth-button"],' +
                    'button[data-testid="apple-auth-button"],' +
                    'a[href*="accounts.google.com"],' +
                    'a[href*="appleid.apple.com"]' +
                    '{ display: none !important; }';
                (document.head || document.documentElement).appendChild(style);

                function hideEl(el) {
                    if (el && el.style.display !== 'none') {
                        el.style.setProperty('display', 'none', 'important');
                    }
                }

                function hideSsoButtons() {
                    var els = document.querySelectorAll('button, a, [role="button"]');
                    for (var i = 0; i < els.length; i++) {
                        var el = els[i];
                        var label = (el.textContent || '') + ' ' +
                                    (el.getAttribute('aria-label') || '');
                        var icon = el.querySelector('img[alt], svg[aria-label]');
                        if (icon) {
                            label += ' ' + (icon.getAttribute('alt') || '') + ' ' +
                                     (icon.getAttribute('aria-label') || '');
                        }
                        label = label.toLowerCase();
                        if (label.indexOf('google') !== -1 ||
                            label.indexOf('apple') !== -1) {
                            hideEl(el);
                        }
                    }
                    var all = document.querySelectorAll('*');
                    for (var j = 0; j < all.length; j++) {
                        var t = (all[j].textContent || '').trim().toLowerCase();
                        if ((t === 'or' || t === '또는') && all[j].children.length === 0) {
                            hideEl(all[j]);
                        }
                    }
                }

                hideSsoButtons();
                try {
                    new MutationObserver(hideSsoButtons).observe(
                        document.documentElement,
                        { childList: true, subtree: true }
                    );
                } catch (e) {}
            })();
        """
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewErrorScreen(
    message: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Claude", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ExtendedTheme.colors.textSecondary)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WebView Unavailable",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = ExtendedTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Install or enable Android System WebView, then try again.",
                fontSize = 14.sp,
                color = ExtendedTheme.colors.textSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebViewScreen(
    onSessionCaptured: (String) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    onStartPolling: ((String) -> Unit) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var showEmailHint by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sign in to Claude",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    try {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = true

                            val webView = this
                            try {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(webView, true)
                                cookieManager.removeAllCookies(null)
                                cookieManager.flush()
                            } catch (_: Exception) {}

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    // Google/Apple OAuth dead-end in a WebView (Google
                                    // shows a "disallowed_useragent" blank page), so
                                    // block the navigation even if a hidden button
                                    // gets tapped.
                                    val host = request?.url?.host
                                    if (request?.isForMainFrame == true &&
                                        (host == "accounts.google.com" ||
                                            host == "appleid.apple.com")
                                    ) {
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false

                                    // Hide social login buttons via JS
                                    view?.evaluateJavascript(
                                        LoginActivity.HIDE_SSO_BUTTONS_JS,
                                        null
                                    )

                                    // Show email hint on login page
                                    if (url?.contains("claude.ai/login") == true) {
                                        showEmailHint = true
                                    } else {
                                        showEmailHint = false
                                    }

                                    // Check for session cookie
                                    checkForSessionCookie(url, onSessionCaptured)

                                    // Start polling after login page (magic link flow)
                                    if (url?.contains("claude.ai") == true) {
                                        onStartPolling(onSessionCaptured)
                                    }
                                }
                            }

                            loadUrl("https://claude.ai/login")
                        }
                    } catch (e: Exception) {
                        onError("Failed to initialize WebView: ${e.message}")
                        android.view.View(context)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Email login hint banner
            if (showEmailHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ClaudePurpleDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Enter your email address to sign in.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ClaudePurple,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

private fun checkForSessionCookie(url: String?, onSessionCaptured: (String) -> Unit) {
    try {
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return

        val sessionKey = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sessionKey=") }
            ?.substringAfter("sessionKey=")
            ?.trim()

        if (!sessionKey.isNullOrBlank()) {
            onSessionCaptured(sessionKey)
        }
    } catch (_: Exception) {}
}
