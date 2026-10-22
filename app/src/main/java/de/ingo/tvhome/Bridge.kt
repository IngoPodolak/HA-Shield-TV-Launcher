package de.ingo.tvhome

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.JavascriptInterface
import okhttp3.Request
import org.json.JSONObject

/** Schnittstelle, über die die Oberfläche (JavaScript) mit Android spricht. Name im JS: Android */
class Bridge(private val activity: MainActivity) {
    private val ctx = activity.applicationContext

    @JavascriptInterface fun getConfig(): String = Config.get(ctx).toString()
    @JavascriptInterface fun saveConfig(patch: String) { try { Config.merge(ctx, JSONObject(patch)) } catch (_: Exception) {} }
    @JavascriptInterface fun getApps(): String = Apps.listJson(ctx)
    @JavascriptInterface fun getIcon(pkg: String): String = Apps.iconDataUrl(ctx, pkg, false)
    @JavascriptInterface fun getBanner(pkg: String): String = Apps.iconDataUrl(ctx, pkg, true)
    @JavascriptInterface fun launch(pkg: String): Boolean = Apps.launch(ctx, pkg)
    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface fun builtinBackgrounds(): String = Backgrounds.builtinJson(ctx)
    @JavascriptInterface fun customBackgrounds(): String = Backgrounds.customJson(ctx)
    @JavascriptInterface fun bingBackgrounds(): String = Backgrounds.bingJson()

    @JavascriptInterface fun setupUrl(): String = SetupServer.url(ctx)
    @JavascriptInterface fun qr(text: String): String = Net.qrDataUrl(text)

    @JavascriptInterface fun canOverlay(): Boolean = Overlay.canShow(ctx)
    @JavascriptInterface fun testPopup() = HaService.ringNow(ctx)

    @JavascriptInterface fun checkUpdate(): String = Updater.check()
    @JavascriptInterface fun installUpdate(url: String): String = Updater.downloadAndInstall(ctx, url)

    @JavascriptInterface fun openAndroidSettings() = start(Intent(Settings.ACTION_SETTINGS))
    @JavascriptInterface fun openOverlaySettings() =
        start(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))

    @JavascriptInterface fun httpGet(url: String): String = try {
        TvHomeApp.http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
    } catch (e: Exception) { "" }

    private fun start(i: Intent) {
        try { activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
