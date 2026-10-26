package de.ingo.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Zweite Seite: zeigt ein Home-Assistant-Dashboard im Vollbild.
 * Die Anmeldung läuft über die offizielle „external auth“-Schnittstelle der HA-Oberfläche
 * (wie bei der Companion-App) mit dem Token, das HA Shield TV Launcher ohnehin schon hat.
 */
class DashboardActivity : Activity() {
    private lateinit var web: WebView
    @Volatile private var dialogOpen = false
    private var mode = "view"
    private var navScript = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = Config.get(this)
        mode = cfg.optString("dashMode", "view")
        val path = cfg.optString("dashPath", "lovelace").trim('/').ifEmpty { "lovelace" }
        val hideSidebar = cfg.optString("dashSidebar", "hide") == "hide"
        navScript = try { assets.open("web/dashnav.js").bufferedReader().readText() } catch (e: Exception) { "" }

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#111111"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(ExternalApp(), "externalApp")
        web.addJavascriptInterface(TvDash(), "TVDash")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val sidebarJs = if (hideSidebar)
                    "if(localStorage.getItem('dockedSidebar')!=='\"always_hidden\"'){localStorage.setItem('dockedSidebar','\"always_hidden\"');location.reload();}"
                else
                    "if(localStorage.getItem('dockedSidebar')==='\"always_hidden\"'){localStorage.setItem('dockedSidebar','\"docked\"');location.reload();}"
                view.evaluateJavascript(sidebarJs, null)
                view.evaluateJavascript("window.TV_DASH_MODE='$mode';window.TV_DASH_PATH='$path';\n$navScript", null)
                applyZoom()
            }
        }
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        setContentView(web)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val base = Config.haUrl(this)
        if (base.isEmpty() || Config.haToken(this).isEmpty()) {
            web.loadData(
                "<body style='background:#111;color:#eee;font:28px sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>" +
                    "Bitte zuerst in den Einstellungen bei Home Assistant anmelden.</body>", "text/html; charset=utf-8", "utf-8"
            )
        } else {
            web.loadUrl("$base/$path?external_auth=1")
        }
        web.requestFocus()
        Config.addListener(zoomListener)
    }

    /** Schnittstelle, die die HA-Oberfläche selbst aufruft (gleiche Namen wie bei der Companion-App). */
    inner class ExternalApp {
        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            val p = try { JSONObject(payload) } catch (e: Exception) { JSONObject() }
            val cb = p.optString("callback").filter { it.isLetterOrDigit() || it == '_' || it == '$' || it == '.' }
            if (cb.isEmpty()) return
            val token = Config.haToken(this@DashboardActivity)
            val data = JSONObject().put("access_token", token).put("expires_in", 1800)
            runOnUiThread {
                if (token.isEmpty()) web.evaluateJavascript("$cb(false)", null)
                else web.evaluateJavascript("$cb(true, $data)", null)
            }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            val cb = (try { JSONObject(payload).optString("callback") } catch (e: Exception) { "" })
                .filter { it.isLetterOrDigit() || it == '_' || it == '$' || it == '.' }
            if (cb.isNotEmpty()) runOnUiThread { web.evaluateJavascript("$cb(true)", null) }
        }

        @JavascriptInterface
        fun externalBus(message: String) {
            val m = try { JSONObject(message) } catch (e: Exception) { return }
            if (m.optString("type") == "config/get") {
                val result = JSONObject()
                    .put("hasSettingsScreen", false).put("canWriteTag", false).put("hasExoPlayer", false)
                    .put("canCommissionMatter", false).put("canImportThreadCredentials", false)
                    .put("hasAssist", false).put("hasBarCodeScanner", 0).put("canSetupImprov", false)
                val reply = JSONObject().put("id", m.optInt("id")).put("type", "result").put("success", true).put("result", result)
                runOnUiThread { web.evaluateJavascript("window.externalBus && window.externalBus($reply)", null) }
            }
        }
    }

    /** Rückmeldungen aus dem Navigations-Skript. */
    inner class TvDash {
        @JavascriptInterface fun setDialog(open: Boolean) { dialogOpen = open }
    }

    private fun js(code: String) = web.evaluateJavascript(code, null)

    /** Zoom wie im Browser: kleiner = mehr Karten nebeneinander. Wirkt sofort, auch bei offenem Dashboard. */
    private fun applyZoom() {
        val z = Config.get(this).optInt("dashZoom", 75).coerceIn(30, 200) / 100.0
        js("document.documentElement.style.zoom='$z';window.dispatchEvent(new Event('resize'));")
    }
    private val zoomListener: () -> Unit = { runOnUiThread { if (::web.isInitialized) applyZoom() } }

    /** Echte Tastendrücke an die Webansicht schicken (für Detailfenster: Tab-Fokus, Regler, Enter). */
    private fun sendKey(code: Int, shift: Boolean = false) {
        val t = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        web.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        web.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isNav = code in listOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER
        )
        if (!isNav) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true

        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) {
                if (dialogOpen) { dialogOpen = false; js("history.back()") } else finish()
            }
            return true
        }
        // Detailfenster offen: Steuerkreuz wie Tastatur (▲▼ = vorheriges/nächstes Element, ◀▶ = Regler, OK = Enter)
        if (dialogOpen) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> sendKey(KeyEvent.KEYCODE_TAB, shift = true)
                KeyEvent.KEYCODE_DPAD_DOWN -> sendKey(KeyEvent.KEYCODE_TAB)
                KeyEvent.KEYCODE_DPAD_LEFT -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
                KeyEvent.KEYCODE_DPAD_RIGHT -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                else -> if (event.repeatCount == 0) sendKey(KeyEvent.KEYCODE_ENTER)
            }
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> js("window.tvDash && tvDash.key('up')")
            KeyEvent.KEYCODE_DPAD_DOWN -> js("window.tvDash && tvDash.key('down')")
            KeyEvent.KEYCODE_DPAD_LEFT -> js("window.tvDash && tvDash.key('left')")
            KeyEvent.KEYCODE_DPAD_RIGHT -> js("window.tvDash && tvDash.key('right')")
            else -> if (event.repeatCount == 0) js("window.tvDash && tvDash.key('ok')")
        }
        return true
    }

    override fun onDestroy() {
        Config.removeListener(zoomListener)
        web.destroy()
        super.onDestroy()
    }
}
