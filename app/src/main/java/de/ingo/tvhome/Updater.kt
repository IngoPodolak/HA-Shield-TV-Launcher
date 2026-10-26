package de.ingo.tvhome

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Holt neue Versionen direkt von GitHub (Releases des Repos). */
object Updater {
    /** {"current":12,"latest":14,"url":"…","available":true} oder {"error":"…"} */
    fun check(): String {
        val out = JSONObject().put("current", BuildConfig.VERSION_CODE)
        return try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            TvHomeApp.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return out.put("error", "GitHub antwortet nicht (${r.code})").toString()
                val rel = JSONObject(r.body!!.string())
                val tag = rel.optString("tag_name").trimStart('v', 'V')
                val latest = tag.toIntOrNull() ?: 0
                val assets = rel.optJSONArray("assets")
                var url = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
                }
                out.put("latest", latest).put("url", url).put("available", latest > BuildConfig.VERSION_CODE && url.isNotEmpty())
                out.toString()
            }
        } catch (e: Exception) {
            out.put("error", "Keine Verbindung zu GitHub").toString()
        }
    }

    /** Lädt die APK herunter. */
    private fun download(ctx: Context, url: String): File? {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "tvhome.apk")
        TvHomeApp.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            f.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
        return f
    }

    /**
     * Neueste Version suchen und installieren. Bevorzugt ohne Rückfrage (über die Debugging-Verbindung),
     * sonst mit dem normalen Android-Installationsdialog. Gibt eine Meldung zurück ("" = läuft).
     */
    fun updateNow(ctx: Context): String {
        val info = JSONObject(check())
        if (info.has("error")) return info.getString("error")
        if (!info.optBoolean("available")) return "Du hast schon die neueste Version (" + BuildConfig.VERSION_NAME + ")."
        val url = info.optString("url")
        val apk = try { download(ctx, url) } catch (e: Exception) { null } ?: return "Download fehlgeschlagen"
        if (Remote.installApk(ctx, apk)) return ""
        return downloadAndInstall(ctx, url)
    }

    /** Lädt die APK und öffnet den Android-Installationsdialog. Gibt eine Fehlermeldung oder "" zurück. */
    fun downloadAndInstall(ctx: Context, url: String): String {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
            return "Bitte erst erlauben, dass TV Home Apps installieren darf, dann nochmal versuchen."
        }
        return try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val f = File(dir, "tvhome.apk")
            TvHomeApp.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return "Download fehlgeschlagen (${r.code})"
                f.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
            }
            val uri = FileProvider.getUriForFile(ctx, "de.ingo.tvhome.files", f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            ""
        } catch (e: Exception) {
            "Update fehlgeschlagen: ${e.message}"
        }
    }
}
