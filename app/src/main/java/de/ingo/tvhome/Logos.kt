package de.ingo.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import java.io.File

/** Eigene Logos pro App, hochgeladen über die Handy-Seite (PNG oder SVG mit transparentem Hintergrund). */
object Logos {
    fun dir(ctx: Context): File = File(ctx.filesDir, "logos").apply { mkdirs() }

    private fun safe(pkg: String) = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Speichert das Logo und stellt die App automatisch auf „Eigenes Logo“ um. */
    fun save(ctx: Context, pkg: String, name: String, data: ByteArray): String {
        val isSvg = name.lowercase().endsWith(".svg") || String(data.copyOfRange(0, minOf(data.size, 512))).contains("<svg")
        var bytes = data
        if (!isSvg) {
            // Rastergrafik: Hat sie einen festen Hintergrund (z. B. JPG oder weißer Rand), wird er entfernt.
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IllegalArgumentException("Bildformat wird nicht unterstützt")
            val w = bmp.width; val h = bmp.height
            val corners = listOf(bmp.getPixel(1, 1), bmp.getPixel(w - 2, 1), bmp.getPixel(1, h - 2), bmp.getPixel(w - 2, h - 2))
            val opaque = corners.count { Color.alpha(it) > 200 } >= 3
            val scale = minOf(1.0, 1600.0 / maxOf(w, h))
            val sw = maxOf(1, (w * scale).toInt()); val sh = maxOf(1, (h * scale).toInt())
            val src = if (scale < 1.0) Bitmap.createScaledBitmap(bmp, sw, sh, true) else bmp
            val outBmp = if (opaque) Apps.toWhite(src, sw, sh, allowGray = false) ?: src else src
            val bos = ByteArrayOutputStream(); outBmp.compress(Bitmap.CompressFormat.PNG, 100, bos); bytes = bos.toByteArray()
        }
        val ext = if (isSvg) "svg" else "png"
        dir(ctx).listFiles()?.filter { it.nameWithoutExtension == safe(pkg) }?.forEach { it.delete() }
        val f = File(dir(ctx), safe(pkg) + "." + ext)
        f.writeBytes(bytes)
        val cfg = Config.get(ctx)
        val logos = cfg.optJSONObject("customLogos") ?: JSONObject()
        logos.put(pkg, "file://" + f.absolutePath + "?v=" + f.lastModified())
        val overrides = cfg.optJSONObject("iconOverrides") ?: JSONObject()
        overrides.put(pkg, "custom")
        Config.merge(ctx, JSONObject().put("customLogos", logos).put("iconOverrides", overrides))
        return f.name
    }

    fun delete(ctx: Context, pkg: String) {
        dir(ctx).listFiles()?.filter { it.nameWithoutExtension == safe(pkg) }?.forEach { it.delete() }
        val cfg = Config.get(ctx)
        val logos = cfg.optJSONObject("customLogos") ?: JSONObject()
        logos.remove(pkg)
        val overrides = cfg.optJSONObject("iconOverrides") ?: JSONObject()
        if (overrides.optString(pkg) == "custom") overrides.remove(pkg)
        Config.merge(ctx, JSONObject().put("customLogos", logos).put("iconOverrides", overrides))
    }

    fun file(ctx: Context, pkg: String): File? =
        dir(ctx).listFiles()?.firstOrNull { it.nameWithoutExtension == safe(pkg) }
}
