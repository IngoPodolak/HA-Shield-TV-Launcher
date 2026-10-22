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
import android.view.WindowManager
import android.webkit.WebView

/** Klingel-Popup, das über jeder App liegt (auch über einem laufenden Film). */
object Overlay {
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private val hide = Runnable { remove() }

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    /** [cameraUrl] darf null sein, dann erscheint nur der Hinweis. */
    @SuppressLint("SetJavaScriptEnabled")
    fun show(ctx: Context, cameraUrl: String?, title: String) {
        main.post {
            if (!canShow(ctx)) return@post
            val cfg = Config.get(ctx)
            val seconds = cfg.optInt("popupSeconds", 30).coerceIn(5, 600)
            val size = cfg.optString("popupSize", "m")
            val pos = cfg.optString("popupPos", "tr")
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = ctx.resources.displayMetrics
            val screenW = maxOf(dm.widthPixels, dm.heightPixels)
            val frac = when (size) { "s" -> 0.26; "l" -> 0.5; else -> 0.36 }
            val w = (screenW * frac).toInt()
            val h = (w * 9 / 16) + (56 * dm.density).toInt()
            val margin = (24 * dm.density).toInt()

            val url = "file:///android_asset/web/popup.html?t=" + Uri.encode(title) +
                (if (cameraUrl != null) "&src=" + Uri.encode(cameraUrl) else "") + "&s=" + seconds

            val existing = view
            if (existing != null) {
                existing.loadUrl(url)
            } else {
                val wv = WebView(ctx)
                wv.setBackgroundColor(Color.TRANSPARENT)
                wv.settings.javaScriptEnabled = true
                wv.settings.allowFileAccess = true
                wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                wv.isFocusable = false
                val lp = WindowManager.LayoutParams(
                    w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
                    wm.addView(wv, lp)
                    view = wv
                    wv.loadUrl(url)
                } catch (e: Exception) {
                    return@post
                }
            }
            main.removeCallbacks(hide)
            main.postDelayed(hide, seconds * 1000L)
        }
    }

    fun remove() {
        main.post {
            val v = view ?: return@post
            view = null
            try {
                (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (_: Exception) {}
            v.destroy()
        }
    }
}
