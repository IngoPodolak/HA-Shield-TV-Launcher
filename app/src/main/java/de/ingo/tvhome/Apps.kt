package de.ingo.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Liest die installierten Apps aus und startet sie. */
object Apps {
    private val iconCache = ConcurrentHashMap<String, ByteArray>()

    data class AppInfo(val pkg: String, val label: String, val hasBanner: Boolean)

    fun list(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val infos = try { pm.queryIntentActivities(intent, 0) } catch (e: Exception) { emptyList() }
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || result.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                val hasBanner = try {
                    ri.activityInfo.loadBanner(pm) != null || pm.getApplicationBanner(pkg) != null
                } catch (e: Exception) { false }
                result[pkg] = AppInfo(pkg, label, hasBanner)
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun listJson(ctx: Context): String {
        val arr = JSONArray()
        for (a in list(ctx)) {
            arr.put(JSONObject().put("pkg", a.pkg).put("label", a.label).put("banner", a.hasBanner))
        }
        return arr.toString()
    }

    private fun banner(ctx: Context, pkg: String): Drawable? {
        val pm = ctx.packageManager
        return try {
            val i = pm.getLeanbackLaunchIntentForPackage(pkg)
            val ri = if (i != null) pm.resolveActivity(i, 0) else null
            ri?.activityInfo?.loadBanner(pm) ?: pm.getApplicationBanner(pkg)
        } catch (e: Exception) { null }
    }

    private fun icon(ctx: Context, pkg: String): Drawable? =
        try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun png(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /**
     * PNG des App-Symbols.
     * style: "color" = Originalsymbol, "banner" = breites TV-Banner, "mono" = weißes Logo ohne Hintergrund.
     */
    fun iconPng(ctx: Context, pkg: String, style: String): ByteArray? {
        val key = "$pkg|$style"
        iconCache[key]?.let { return it }
        val bytes: ByteArray? = try {
            when (style) {
                "banner" -> {
                    val b = banner(ctx, pkg)
                    if (b != null) png(render(b, 320, 180)) else icon(ctx, pkg)?.let { png(render(it, 192, 192)) }
                }
                "mono" -> mono(ctx, pkg)?.let { png(it) } ?: icon(ctx, pkg)?.let { png(render(it, 192, 192)) }
                else -> icon(ctx, pkg)?.let { png(render(it, 192, 192)) }
            }
        } catch (e: Exception) { null }
        if (bytes != null) iconCache[key] = bytes
        return bytes
    }

    fun iconDataUrl(ctx: Context, pkg: String, style: String): String {
        val png = iconPng(ctx, pkg, style) ?: return ""
        return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
    }

    /**
     * Macht aus dem App-Symbol ein weißes Logo: Hintergrund wird entfernt, nur die Form des Logos bleibt.
     * Bei modernen Symbolen wird die Vordergrund-Ebene genutzt, sonst die Hintergrundfarbe aus den Ecken erkannt.
     */
    private fun mono(ctx: Context, pkg: String): Bitmap? {
        val d = icon(ctx, pkg) ?: return null
        val size = 192
        var src: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable && d.foreground != null) {
            val fg = d.foreground!!
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val ext = size / 4
            fg.setBounds(-ext, -ext, size + ext, size + ext)
            fg.draw(Canvas(bmp))
            src = bmp
        }
        val first = src?.let { toWhite(it) }
        if (first != null) return first
        return toWhite(render(d, size, size))
    }

    private fun toWhite(bmp: Bitmap): Bitmap? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val inset = 4
        val corners = intArrayOf(
            px[inset * w + inset], px[inset * w + (w - 1 - inset)],
            px[(h - 1 - inset) * w + inset], px[(h - 1 - inset) * w + (w - 1 - inset)]
        )
        val opaqueCorners = corners.filter { Color.alpha(it) > 200 }
        val useBg = opaqueCorners.size >= 3
        val br = if (useBg) opaqueCorners.map { Color.red(it) }.average() else 0.0
        val bgc = if (useBg) opaqueCorners.map { Color.green(it) }.average() else 0.0
        val bb = if (useBg) opaqueCorners.map { Color.blue(it) }.average() else 0.0

        // 1. Durchgang: Deckkraft (Hintergrund raus) und Helligkeit jedes Pixels bestimmen.
        val alpha = IntArray(w * h)
        val lum = IntArray(w * h)
        val hist = IntArray(256)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1; var covered = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val p = px[i]
            var a = Color.alpha(p)
            if (useBg && a > 0) {
                val dist = Math.abs(Color.red(p) - br) + Math.abs(Color.green(p) - bgc) + Math.abs(Color.blue(p) - bb)
                a = (a * ((dist - 45) * 3).coerceIn(0.0, 255.0) / 255.0).toInt()
            }
            alpha[i] = a
            val l = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt().coerceIn(0, 255)
            lum[i] = l
            if (a > 24) {
                covered++
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            if (a > 160) hist[l]++
        }
        // 2. Helligkeitsbereich des Logos (ohne Ausreißer an den Kanten).
        val strong = hist.sum()
        var lo = 0; var hi = 255
        if (strong > 0) {
            var acc = 0
            for (v in 0..255) { acc += hist[v]; if (acc >= strong * 0.05) { lo = v; break } }
            acc = 0
            for (v in 255 downTo 0) { acc += hist[v]; if (acc >= strong * 0.05) { hi = v; break } }
        }
        // Würden in reinem Weiß innere Details verloren gehen (z. B. Schrift auf einer Farbfläche)?
        // Dafür innere Kanten (Helligkeitssprung innerhalb des Logos) mit der Außenkontur vergleichen.
        var innerEdges = 0; var outerEdges = 0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            if (alpha[i] <= 160) continue
            for (j in intArrayOf(i + 1, i + w)) {
                if (alpha[j] > 160) { if (Math.abs(lum[i] - lum[j]) > 40) innerEdges++ }
                else if (alpha[j] < 60) outerEdges++
            }
        }
        val multiTone = hi - lo > 45 && innerEdges > outerEdges * 0.25
        // 3. Graustufen: einfarbige Logos werden reinweiß, mehrfarbige behalten ihre Abstufungen (hell bis mittelgrau).
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val g = if (multiTone) (130 + (lum[i] - lo).coerceIn(0, hi - lo) * 125 / (hi - lo)) else 255
            out[i] = Color.argb(alpha[i], g, g, g)
        }
        val ratio = covered.toDouble() / (w * h)
        if (maxX < 0 || ratio < 0.01 || ratio > 0.8) return null
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        // Logo zuschneiden und mittig in ein Quadrat setzen, damit alle Logos gleich groß wirken.
        val cw = maxX - minX + 1; val ch = maxY - minY + 1
        val crop = Bitmap.createBitmap(res, minX, minY, cw, ch)
        val side = 192
        val pad = 4
        val scale = minOf((side - 2 * pad).toFloat() / cw, (side - 2 * pad).toFloat() / ch)
        val sw = (cw * scale).toInt().coerceAtLeast(1); val sh = (ch * scale).toInt().coerceAtLeast(1)
        val outBmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(outBmp).drawBitmap(Bitmap.createScaledBitmap(crop, sw, sh, true), ((side - sw) / 2).toFloat(), ((side - sh) / 2).toFloat(), null)
        return outBmp
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }
}
