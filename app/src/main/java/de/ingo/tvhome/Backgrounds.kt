package de.ingo.tvhome

import android.content.Context
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Hintergrundbilder: eingebaute Fotos, eigene Fotos vom Handy, Bing Bild des Tages. */
object Backgrounds {
    fun customDir(ctx: Context): File = File(ctx.filesDir, "bg").apply { mkdirs() }

    fun builtinJson(ctx: Context): String {
        return try {
            val txt = ctx.assets.open("backgrounds/list.json").bufferedReader().readText()
            val arr = JSONArray(txt)
            val existing = (ctx.assets.list("backgrounds") ?: emptyArray()).toSet()
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val file = o.getString("id") + ".jpg"
                if (file in existing) {
                    out.put(JSONObject().put("id", o.getString("id")).put("name", o.optString("name"))
                        .put("url", "file:///android_asset/backgrounds/$file")
                        .put("thumb", "file:///android_asset/backgrounds/thumbs/$file"))
                }
            }
            out.toString()
        } catch (e: Exception) { "[]" }
    }

    fun customJson(ctx: Context): String {
        val out = JSONArray()
        customDir(ctx).listFiles()?.sortedBy { it.name }?.forEach { f ->
            out.put(JSONObject().put("id", "custom-" + f.name).put("name", f.nameWithoutExtension)
                .put("file", f.name).put("url", "file://" + f.absolutePath + "?v=" + f.lastModified()))
        }
        return out.toString()
    }

    fun saveCustom(ctx: Context, name: String, data: ByteArray): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "foto" }
        val base = safe.substringBeforeLast('.')
        val f = File(customDir(ctx), "${System.currentTimeMillis() / 1000}_$base.jpg")
        f.writeBytes(data)
        return f.name
    }

    fun deleteCustom(ctx: Context, file: String): Boolean {
        val f = File(customDir(ctx), File(file).name)
        return f.exists() && f.delete()
    }

    /** Die letzten Bing-Bilder des Tages (bis zu 8) in hoher Auflösung. */
    fun bingJson(): String {
        return try {
            val req = Request.Builder()
                .url("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=de-DE")
                .build()
            TvHomeApp.http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return "[]"
                val images = JSONObject(body).getJSONArray("images")
                val out = JSONArray()
                for (i in 0 until images.length()) {
                    val im = images.getJSONObject(i)
                    val base = im.optString("urlbase")
                    val url = if (base.isNotEmpty()) "https://www.bing.com${base}_UHD.jpg"
                    else "https://www.bing.com" + im.getString("url")
                    out.put(JSONObject().put("id", "bing-$i").put("name", im.optString("title"))
                        .put("info", im.optString("copyright")).put("url", url))
                }
                out.toString()
            }
        } catch (e: Exception) { "[]" }
    }
}
