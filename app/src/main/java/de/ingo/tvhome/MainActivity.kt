package de.ingo.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val configListener: () -> Unit = {
        runOnUiThread { js("window.tvConfigChanged && tvConfigChanged()") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(this), "Android")
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        setContentView(web)
        hideSystemUi()
        web.loadUrl("file:///android_asset/web/index.html")
        web.requestFocus()
        Config.addListener(configListener)
        TvHomeApp.startHaService(this)
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun js(code: String) {
        if (::web.isInitialized) web.evaluateJavascript(code, null)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        web.requestFocus()
        js("window.tvResume && tvResume()")
    }

    override fun onPause() {
        super.onPause()
        js("window.tvPause && tvPause()")
    }

    override fun onDestroy() {
        Config.removeListener(configListener)
        super.onDestroy()
    }

    /** Home-Taste, während TV Home schon offen ist: zurück zur Startansicht. */
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        js("window.tvHome && tvHome()")
    }

    private val okHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var okDown = false
    private var okLongFired = false
    private val okLong = Runnable {
        if (okDown && !okLongFired) { okLongFired = true; js("window.tvOk && tvOk(true)") }
    }

    /**
     * Zurück-Taste: Die Oberfläche entscheidet (Menü schließen usw.). Ein Launcher wird nie beendet.
     * OK-Taste: kurz und lang drücken wird hier erkannt, weil die Fernbedienung das lange Drücken
     * nicht zuverlässig an die Webansicht weitergibt.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) js("window.tvBack && tvBack()")
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        okDown = true; okLongFired = false
                        okHandler.removeCallbacks(okLong)
                        okHandler.postDelayed(okLong, 600)
                    } else if (!okLongFired && event.eventTime - event.downTime >= 600) {
                        okHandler.removeCallbacks(okLong)
                        okLong.run()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    okHandler.removeCallbacks(okLong)
                    if (okDown && !okLongFired) js("window.tvOk && tvOk(false)")
                    okDown = false
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
