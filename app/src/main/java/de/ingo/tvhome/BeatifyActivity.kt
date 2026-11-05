package de.ingo.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Beatify (Musik-Quiz aus Home Assistant) im Vollbild: zeigt die TV-Anzeige mit QR-Code,
 * Punkten und Bestenliste. Gespielt und gesteuert wird am Handy, die Musik kommt über
 * den in Beatify gewählten Lautsprecher. Zurück-Taste schließt die Anzeige.
 */
class BeatifyActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0b0b12"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { view.evaluateJavascript(BeatifyOverlay.fitJs(this@BeatifyActivity), null) }
            override fun onReceivedError(view: WebView, code: Int, desc: String?, url: String?) {
                show("Beatify ist nicht erreichbar. Läuft Home Assistant und ist Beatify installiert?")
            }
        }
        setContentView(web)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val base = Config.haUrl(this)
        if (base.isEmpty()) show("Bitte zuerst in den Einstellungen die Home-Assistant-Adresse eintragen.")
        else web.loadUrl("$base/beatify/static/dashboard.html")
        web.requestFocus()
    }

    private fun show(text: String) = web.loadDataWithBaseURL(null,
        "<body style='background:#0b0b12;color:#eee;font:28px sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:0 10vw;box-sizing:border-box'>$text</body>",
        "text/html", "utf-8", null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
