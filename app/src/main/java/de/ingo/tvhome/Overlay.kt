package de.ingo.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Popups über jeder App (auch über einem laufenden Film): Klingel und Nachrichten.
 * Solange es sichtbar ist, schließt jede Taste der Fernbedienung (Zurück, OK, Pfeile) das Popup.
 */
object Overlay {
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var web: WebView? = null
    private val hide = Runnable { remove(byUser = false) }

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    /** Fenster-Container, der die Tasten abfängt, solange das Popup sichtbar ist. */
    private class KeyCatcher(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_UP) remove(byUser = true)
            return true
        }
    }

    /** Klingel-Popup. [cameraUrl] darf null sein, dann erscheint nur der Hinweis. */
    fun show(ctx: Context, cameraUrl: String?, title: String) {
        val cfg = Config.get(ctx)
        val seconds = cfg.optInt("popupSeconds", 30).coerceIn(5, 600)
        val aspect = when (cfg.optString("camAspect", "4:3")) { "16:9" -> 9.0 / 16.0; "1:1" -> 1.0; else -> 3.0 / 4.0 }
        val frac = when (cfg.optString("popupSize", "m")) { "s" -> 0.24; "l" -> 0.42; else -> 0.32 }
        val url = "file:///android_asset/web/popup.html?t=" + Uri.encode(title) +
            (if (cameraUrl != null) "&src=" + Uri.encode(cameraUrl) else "") + "&s=" + seconds
        open(ctx, url, seconds, frac, aspect, autoHeight = false, pos = cfg.optString("popupPos", "tr"))
    }

    /** Nachricht (Text und/oder Bild). Die Höhe passt sich dem Inhalt an. */
    fun showMessage(ctx: Context, title: String, message: String, imageFile: String?, seconds: Int) {
        val cfg = Config.get(ctx)
        val frac = when (cfg.optString("msgSize", "m")) { "s" -> 0.26; "l" -> 0.44; else -> 0.34 }
        val url = "file:///android_asset/web/popup.html?mode=msg&t=" + Uri.encode(title) + "&m=" + Uri.encode(message) +
            (if (imageFile != null) "&img=" + Uri.encode("file://$imageFile") else "") + "&s=" + seconds
        open(ctx, url, seconds, frac, if (imageFile != null) 0.75 else 0.25, autoHeight = true, pos = cfg.optString("msgPos", "tr"))
    }

    private var lp: WindowManager.LayoutParams? = null

    /** Wird von popup.html aufgerufen, sobald der Inhalt steht: Fensterhöhe an den Inhalt anpassen. */
    class PopupHost(private val ctx: Context) {
        @JavascriptInterface
        fun resize(cssHeight: Int) {
            main.post {
                val r = root ?: return@post
                val p = lp ?: return@post
                val dm = ctx.resources.displayMetrics
                val maxH = minOf(dm.widthPixels, dm.heightPixels) - (56 * dm.density).toInt()
                p.height = (cssHeight * dm.density).toInt().coerceIn((80 * dm.density).toInt(), maxH)
                try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(r, p) } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun open(ctx: Context, url: String, seconds: Int, frac: Double, aspect: Double, autoHeight: Boolean, pos: String) {
        main.post {
            if (!canShow(ctx)) return@post
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = ctx.resources.displayMetrics
            val screenW = maxOf(dm.widthPixels, dm.heightPixels)
            val screenH = minOf(dm.widthPixels, dm.heightPixels)
            val header = (52 * dm.density).toInt()
            val margin = (28 * dm.density).toInt()
            var w = (screenW * frac).toInt()
            var h = (w * aspect).toInt() + header
            val maxH = screenH - 2 * margin
            if (h > maxH) { h = maxH; w = ((h - header) / aspect).toInt() }

            // Ein altes Popup wird ersetzt (neue Größe/Position)
            root?.let { old ->
                try { wm.removeView(old) } catch (_: Exception) {}
                web?.destroy()
                root = null; web = null
            }
            val container = KeyCatcher(ctx)
            container.isFocusable = true
            container.isFocusableInTouchMode = true
            val wv = WebView(ctx)
            wv.setBackgroundColor(Color.TRANSPARENT)
            wv.settings.javaScriptEnabled = true
            wv.settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            wv.settings.allowFileAccessFromFileURLs = true
            wv.settings.textZoom = 100
            wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            wv.isFocusable = false
            wv.isFocusableInTouchMode = false
            if (autoHeight) wv.addJavascriptInterface(PopupHost(ctx.applicationContext), "PopupHost")
            container.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val p = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = when (pos) {
                "tl" -> Gravity.TOP or Gravity.START
                "bl" -> Gravity.BOTTOM or Gravity.START
                "br" -> Gravity.BOTTOM or Gravity.END
                "tc" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                "c" -> Gravity.CENTER
                else -> Gravity.TOP or Gravity.END
            }
            p.x = if (pos == "tc" || pos == "c") 0 else margin
            p.y = if (pos == "c") 0 else margin
            try {
                wm.addView(container, p)
                root = container; web = wv; lp = p
                wv.loadUrl(url)
                container.requestFocus()
            } catch (e: Exception) {
                return@post
            }
            main.removeCallbacks(hide)
            main.postDelayed(hide, seconds * 1000L)
        }
    }

    fun remove(byUser: Boolean = false) {
        main.post {
            main.removeCallbacks(hide)
            val r = root ?: return@post
            val wv = web
            root = null
            web = null
            try {
                (r.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(r)
            } catch (_: Exception) {}
            wv?.destroy()
            Media.resumeIfWanted(r.context.applicationContext, byUser)
        }
    }
}
