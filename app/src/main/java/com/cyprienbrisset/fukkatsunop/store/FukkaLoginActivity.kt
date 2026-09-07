package com.cyprienbrisset.fukkatsunop.store

// TEMPORARY SPIKE: FukkaStore Google login screen.
// WebView EmbeddedSetup flow replicating Aurora's GoogleLoginScreen: scrape oauth_token from
// cookies + email from the profile DOM, then run the corrected AC2DM exchange and a sanity search.
// Final GO/NO-GO requires a real human login; this screen shows the outcome on-screen.

import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup"
private const val AUTH_TOKEN = "oauth_token"

// Google's EmbeddedSetup post-login page renders the account as
// <div data-profile-identifier data-email="user@gmail.com">...</div>.
private const val JS_PROFILE_EMAIL = """
    (function(){var el=document.querySelector('[data-profile-identifier][data-email]');return el?el.getAttribute('data-email'):null;})();
"""

class FukkaLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FukkaLoginScreen()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun FukkaLoginScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Ouverture de la connexion Google…") }
    // Guard so the AC2DM pipeline only fires once even though onPageFinished can fire repeatedly.
    val fired = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = status,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val cookieManager = CookieManager.getInstance()
                WebView(ctx).apply {
                    cookieManager.removeAllCookies(null)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val cookies = CookieManager.getInstance().getCookie(url) ?: return
                            val cookieMap = parseCookieString(cookies)
                            val oauthToken = cookieMap[AUTH_TOKEN] ?: return
                            if (!fired.compareAndSet(false, true)) return

                            view.evaluateJavascript(JS_PROFILE_EMAIL) { result ->
                                val email = result?.trim('"')?.takeIf {
                                    it.isNotBlank() && it != "null"
                                }
                                if (email == null) {
                                    status = "ERREUR: email introuvable"
                                    return@evaluateJavascript
                                }
                                status = "Connexion en cours pour $email…"
                                scope.launch {
                                    var step = "AC2DM"
                                    try {
                                        val aas = exchangeAasToken(email, oauthToken)
                                        step = "build"
                                        val authData = buildAuthDataFromAas(context, email, aas)
                                        step = "search"
                                        val names = searchTitles(authData, "spotify")
                                        status = "OK n=${names.size} $names"
                                        FukkaAccount(context).save(email, aas)
                                        (context as? android.app.Activity)?.let {
                                            it.setResult(android.app.Activity.RESULT_OK)
                                            it.finish()
                                        }
                                    } catch (e: Exception) {
                                        status = "ERREUR ($step): ${e.message}"
                                    }
                                }
                            }
                        }
                    }

                    settings.apply {
                        allowContentAccess = true
                        domStorageEnabled = true
                        javaScriptEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    loadUrl(EMBEDDED_SETUP_URL)
                }
            },
        )
    }
}

/** Parse a WebView cookie string ("k=v; k2=v2") into a map. */
private fun parseCookieString(cookies: String): Map<String, String> {
    val map = HashMap<String, String>()
    cookies.split("; ").forEach { part ->
        val kv = part.split("=", limit = 2)
        if (kv.size >= 2) map[kv[0]] = kv[1]
    }
    return map
}
