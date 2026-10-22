package de.ingo.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient

/** Zeigt die Home-Assistant-Anmeldeseite direkt am Fernseher. */
class LoginActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val haUrl = intent.getStringExtra("haUrl") ?: Config.haUrl(this)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#111111"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("/auth-callback") && view.title == "TVHOME_OK") {
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2500)
                }
            }
        }
        setContentView(web)
        web.loadUrl(Auth.authorizeUrl(this, haUrl, "http://127.0.0.1:${SetupServer.PORT}"))
        web.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (web.canGoBack()) web.goBack() else finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
