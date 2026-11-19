package de.ingo.beatifytv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Beatify TV: zeigt die TV-Ansicht des Beatify-Musikquiz aus Home Assistant im Vollbild.
 * - Seite wird für 1920 px Breite ausgelegt (sonst überlappen die Elemente auf 1080p-Geräten)
 * - „Ton an“ wird automatisch bestätigt, damit der Lautsprecher „TV-Bildschirm“ ohne Tippen funktioniert
 * - OK oder Menü-Taste: Einstellungen (Adresse, Größe); Zurück: beenden
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("beatify", Context.MODE_PRIVATE) }
    private var showingError = false

    private val base get() = prefs.getString("url", "https://homeassistant.podolan.de")!!.trimEnd('/')
    private val zoom get() = prefs.getInt("zoom", 100)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0b0b14"))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.isFocusable = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!showingError) view.evaluateJavascript(fitJs(), null)
            }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showError()
            }
        }
        setContentView(web)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        load()
        if (!prefs.contains("url")) web.post { showSettings() }
    }

    private fun load() {
        showingError = false
        web.loadUrl("$base/beatify/static/dashboard.html")
    }

    private fun fitJs(): String {
        val w = Math.round(1920 * 100.0 / zoom.coerceIn(50, 200))
        return """
            (function(){
              var m=document.querySelector('meta[name=viewport]');
              if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
              var s=(screen.width||960)/$w;
              m.content='width=$w,initial-scale='+s+',minimum-scale='+s+',maximum-scale='+s+',user-scalable=no';
              if(!window.__tvSound){window.__tvSound=setInterval(function(){
                var b=document.getElementById('dashboard-sound-gate-btn');
                if(b&&b.offsetParent!==null){b.click();}
              },1500);}
            })()
        """.trimIndent()
    }

    private fun showError() {
        showingError = true
        web.loadDataWithBaseURL(null,
            "<body style='background:#0b0b14;color:#eee;font:28px sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:0 10vw;box-sizing:border-box'>" +
                "<div style='font-size:40px;font-weight:bold;color:#22d3ee;margin-bottom:24px'>Beatify ist nicht erreichbar</div>" +
                "<div>Adresse: $base</div><div style='opacity:.6;margin-top:24px'>OK drücken für Einstellungen · neuer Versuch in 10 Sekunden</div></body>",
            "text/html", "utf-8", null)
        web.postDelayed({ if (showingError) load() }, 10_000)
    }

    private fun showSettings() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        box.addView(TextView(this).apply { text = "Adresse von Home Assistant" })
        val input = EditText(this).apply {
            setText(base)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        box.addView(input)
        val sizes = intArrayOf(70, 80, 90, 100, 110, 125, 150)
        val labels = sizes.map { "$it %" }.toTypedArray()
        var chosen = sizes.indexOf(zoom).let { if (it < 0) 3 else it }
        box.addView(TextView(this).apply { text = "Größe der Anzeige: ${labels[chosen]}"; setPadding(0, pad / 2, 0, 0); id = View.generateViewId(); tag = "sizeLabel" })
        AlertDialog.Builder(this)
            .setTitle("Beatify TV")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                var u = input.text.toString().trim()
                if (u.isNotEmpty() && !u.contains("://")) u = "https://$u"
                if (u.isNotEmpty()) prefs.edit().putString("url", u.trimEnd('/')).putInt("zoom", sizes[chosen]).apply()
                load()
            }
            .setNeutralButton("Größe ändern") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Größe der Anzeige")
                    .setSingleChoiceItems(labels, chosen) { d, which ->
                        chosen = which
                        prefs.edit().putInt("zoom", sizes[which]).apply()
                        d.dismiss()
                        load()
                    }
                    .show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) {
            if (event.action == KeyEvent.ACTION_UP) showSettings()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
