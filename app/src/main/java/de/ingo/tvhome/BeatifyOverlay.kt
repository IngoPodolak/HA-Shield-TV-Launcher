package de.ingo.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Beatify als bildschirmfüllende Ebene über allem anderen.
 * Wird der Fernseher selbst als Lautsprecher gewählt, startet Music Assistant (Cast) eine eigene
 * Wiedergabe-App im Vordergrund – die Musik läuft darunter weiter, die Quiz-Anzeige bleibt sichtbar.
 * Zurück-Taste schließt die Anzeige.
 */
object BeatifyOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var web: WebView? = null

    val isOpen get() = root != null

    private class Catcher(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) close(context)
                return true
            }
            // Lautstärke usw. ans System weitergeben
            return false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun show(ctx: Context): Boolean {
        if (!Overlay.canShow(ctx)) return false
        val app = ctx.applicationContext
        main.post {
            removeNow(app)
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val c = Catcher(app)
            c.isFocusable = true
            c.isFocusableInTouchMode = true
            c.setBackgroundColor(Color.parseColor("#0b0b12"))
            val wv = WebView(app)
            wv.setBackgroundColor(Color.parseColor("#0b0b12"))
            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                textZoom = 100
            }
            wv.isFocusable = false
            wv.isFocusableInTouchMode = false
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, code: Int, desc: String?, url: String?) {
                    message(view, "Beatify ist nicht erreichbar. Läuft Home Assistant und ist Beatify installiert?")
                }
            }
            c.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            @Suppress("DEPRECATION")
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE
            )
            try { wm.addView(c, lp) } catch (e: Exception) { return@post }
            root = c; web = wv
            val base = Config.haUrl(app)
            if (base.isEmpty()) message(wv, "Bitte zuerst in den Einstellungen die Home-Assistant-Adresse eintragen.")
            else wv.loadUrl("$base/beatify/static/dashboard.html")
            c.requestFocus()
        }
        return true
    }

    private fun message(wv: WebView, text: String) = wv.loadDataWithBaseURL(null,
        "<body style='background:#0b0b12;color:#eee;font:28px sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:0 10vw;box-sizing:border-box'>$text<br><br><small style='opacity:.5'>Zurück-Taste zum Schließen</small></body>",
        "text/html", "utf-8", null)

    fun close(ctx: Context) { main.post { removeNow(ctx) } }

    private fun removeNow(ctx: Context) {
        val r = root ?: return
        try { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(r) } catch (_: Exception) {}
        web?.destroy()
        root = null; web = null
    }
}
