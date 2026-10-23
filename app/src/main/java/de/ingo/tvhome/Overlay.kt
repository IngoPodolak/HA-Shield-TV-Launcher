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
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Klingel-Popup, das über jeder App liegt (auch über einem laufenden Film).
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

    /** [cameraUrl] darf null sein, dann erscheint nur der Hinweis. */
    @SuppressLint("SetJavaScriptEnabled")
    fun show(ctx: Context, cameraUrl: String?, title: String) {
        main.post {
            if (!canShow(ctx)) return@post
            val cfg = Config.get(ctx)
            val seconds = cfg.optInt("popupSeconds", 30).coerceIn(5, 600)
            val size = cfg.optString("popupSize", "m")
            val pos = cfg.optString("popupPos", "tr")
            val aspect = when (cfg.optString("camAspect", "4:3")) { "16:9" -> 9.0 / 16.0; "1:1" -> 1.0; else -> 3.0 / 4.0 }
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = ctx.resources.displayMetrics
            val screenW = maxOf(dm.widthPixels, dm.heightPixels)
            val screenH = minOf(dm.widthPixels, dm.heightPixels)
            val frac = when (size) { "s" -> 0.24; "l" -> 0.42; else -> 0.32 }
            val header = (52 * dm.density).toInt()
            val margin = (28 * dm.density).toInt()
            var w = (screenW * frac).toInt()
            var h = (w * aspect).toInt() + header
            val maxH = screenH - 2 * margin
            if (h > maxH) { h = maxH; w = ((h - header) / aspect).toInt() }

            val url = "file:///android_asset/web/popup.html?t=" + Uri.encode(title) +
                (if (cameraUrl != null) "&src=" + Uri.encode(cameraUrl) else "") + "&s=" + seconds

            val existingWeb = web
            if (existingWeb != null) {
                existingWeb.loadUrl(url)
            } else {
                val container = KeyCatcher(ctx)
                container.isFocusable = true
                container.isFocusableInTouchMode = true
                val wv = WebView(ctx)
                wv.setBackgroundColor(Color.TRANSPARENT)
                wv.settings.javaScriptEnabled = true
                wv.settings.allowFileAccess = true
                wv.settings.textZoom = 100
                wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                wv.isFocusable = false
                wv.isFocusableInTouchMode = false
                container.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

                val lp = WindowManager.LayoutParams(
                    w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = when (pos) {
                    "tl" -> Gravity.TOP or Gravity.START
                    "bl" -> Gravity.BOTTOM or Gravity.START
                    "br" -> Gravity.BOTTOM or Gravity.END
                    else -> Gravity.TOP or Gravity.END
                }
                lp.x = margin
                lp.y = margin
                try {
                    wm.addView(container, lp)
                    root = container
                    web = wv
                    wv.loadUrl(url)
                    container.requestFocus()
                } catch (e: Exception) {
                    return@post
                }
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
