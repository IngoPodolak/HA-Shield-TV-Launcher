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
import android.util.DisplayMetrics
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

    /** Grafik in der höchsten Auflösung laden, die die App mitbringt (statt der kleinen TV-Variante). */
    private fun hiRes(ctx: Context, pkg: String, wantBanner: Boolean): Drawable? {
        return try {
            val pm = ctx.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            val act = if (i != null) pm.resolveActivity(i, 0)?.activityInfo else null
            val id = if (wantBanner) (act?.banner?.takeIf { it != 0 } ?: ai.banner)
            else (act?.iconResource?.takeIf { it != 0 } ?: ai.icon)
            if (id == 0) null
            else pm.getResourcesForApplication(ai).getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
        } catch (e: Exception) { null }
    }

    private fun banner(ctx: Context, pkg: String): Drawable? {
        hiRes(ctx, pkg, true)?.let { return it }
        val pm = ctx.packageManager
        return try {
            val i = pm.getLeanbackLaunchIntentForPackage(pkg)
            val ri = if (i != null) pm.resolveActivity(i, 0) else null
            ri?.activityInfo?.loadBanner(pm) ?: pm.getApplicationBanner(pkg)
        } catch (e: Exception) { null }
    }

    private fun icon(ctx: Context, pkg: String): Drawable? =
        hiRes(ctx, pkg, false) ?: try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    /** Banner groß genug rendern: mindestens 640×360, mehr wenn die Grafik selbst größer ist. */
    private fun renderBanner(d: Drawable): Bitmap {
        val w = maxOf(640, d.intrinsicWidth)
        return render(d, w, w * 9 / 16)
    }

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
                    if (b != null) png(renderBanner(b)) else icon(ctx, pkg)?.let { png(render(it, 384, 384)) }
                }
                "mono" -> mono(ctx, pkg)?.let { png(it) } ?: icon(ctx, pkg)?.let { png(render(it, 384, 384)) }
                // Weißer Schriftzug aus dem TV-Banner; ohne Banner das weiße Symbol.
                "bannermono" -> banner(ctx, pkg)?.let { toWhite(renderBanner(it), 720, 240, allowGray = false) }?.let { png(it) }
                    ?: mono(ctx, pkg)?.let { png(it) } ?: icon(ctx, pkg)?.let { png(render(it, 384, 384)) }
                else -> icon(ctx, pkg)?.let { png(render(it, 384, 384)) }
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
        val size = 384
        var src: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable && d.foreground != null) {
            val fg = d.foreground!!
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val ext = size / 4
            fg.setBounds(-ext, -ext, size + ext, size + ext)
            fg.draw(Canvas(bmp))
            src = bmp
        }
        val first = src?.let { toWhite(it, size, size) }
        if (first != null) return first
        return toWhite(render(d, size, size), size, size)
    }

    private fun toWhite(bmp: Bitmap, outW: Int, outH: Int, allowGray: Boolean = true): Bitmap? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Hintergrund schätzen: Bild in Felder teilen (Banner 16×9, Symbole 2×2) und je Feld die vorherrschende Farbe (Median) nehmen.
        // Dünne Schrift und Logos fallen dabei kaum ins Gewicht, Verläufe und Lichtscheine werden so mit erkannt.
        val gx = if (allowGray) 2 else 16; val gy = if (allowGray) 2 else 9
        val cellR = DoubleArray(gx * gy); val cellG = DoubleArray(gx * gy); val cellB = DoubleArray(gx * gy)
        var opaqueCells = 0
        for (cy in 0 until gy) for (cx in 0 until gx) {
            val x0 = cx * w / gx; val x1 = (cx + 1) * w / gx; val y0 = cy * h / gy; val y1 = (cy + 1) * h / gy
            val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
            var yy = y0
            while (yy < y1) { var xx = x0; while (xx < x1) {
                val p = px[yy * w + xx]
                if (Color.alpha(p) > 200) { rs.add(Color.red(p)); gs.add(Color.green(p)); bs.add(Color.blue(p)) }
                xx += 2 }; yy += 2 }
            val k = cy * gx + cx
            if (rs.size > 4) {
                rs.sort(); gs.sort(); bs.sort()
                cellR[k] = rs[rs.size / 2].toDouble(); cellG[k] = gs[gs.size / 2].toDouble(); cellB[k] = bs[bs.size / 2].toDouble()
                opaqueCells++
            } else { cellR[k] = -1.0 }
        }
        // Undurchsichtiger Hintergrund nur, wenn fast alle Felder deckend sind (sonst hat das Bild schon Transparenz).
        val useBg = opaqueCells >= gx * gy * 0.85
        fun bgAt(x: Int, y: Int, out: DoubleArray) {
            val fx = (x.toDouble() / w * gx - 0.5).coerceIn(0.0, gx - 1.0)
            val fy = (y.toDouble() / h * gy - 0.5).coerceIn(0.0, gy - 1.0)
            val x0 = fx.toInt(); val y0 = fy.toInt(); val x1 = minOf(x0 + 1, gx - 1); val y1 = minOf(y0 + 1, gy - 1)
            val tx = fx - x0; val ty = fy - y0
            val ids = intArrayOf(y0 * gx + x0, y0 * gx + x1, y1 * gx + x0, y1 * gx + x1)
            val ws = doubleArrayOf((1 - tx) * (1 - ty), tx * (1 - ty), (1 - tx) * ty, tx * ty)
            var r = 0.0; var g = 0.0; var b = 0.0; var sw = 0.0
            for (n in 0..3) if (cellR[ids[n]] >= 0) { r += cellR[ids[n]] * ws[n]; g += cellG[ids[n]] * ws[n]; b += cellB[ids[n]] * ws[n]; sw += ws[n] }
            if (sw > 0) { out[0] = r / sw; out[1] = g / sw; out[2] = b / sw }
        }
        val bgv = DoubleArray(3)

        // 1. Durchgang: Deckkraft (Hintergrund raus) und Helligkeit jedes Pixels bestimmen.
        val alpha = IntArray(w * h)
        val lum = IntArray(w * h)
        val hist = IntArray(256)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1; var covered = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                var a = Color.alpha(p)
                if (useBg && a > 0) {
                    bgAt(x, y, bgv)
                    val dist = Math.abs(Color.red(p) - bgv[0]) + Math.abs(Color.green(p) - bgv[1]) + Math.abs(Color.blue(p) - bgv[2])
                    a = (a * ((dist - 35) / 100.0).coerceIn(0.0, 1.0)).toInt()
                }
                if (a < 30) a = 0
                alpha[i] = a
                val l = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt().coerceIn(0, 255)
                lum[i] = l
                if (a > 90) {
                    covered++
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
                // Nur voll deckende Pixel zählen für die Farbtöne (Kantenglättung verfälscht sonst das Ergebnis).
                if (a >= 250) hist[l]++
            }
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
        // Nur deutliche Helligkeitssprünge zwischen voll deckenden Pixeln zählen (keine Schattierungen, keine Kantenglättung).
        var innerEdges = 0; var outerEdges = 0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            if (alpha[i] <= 160) continue
            for (j in intArrayOf(i + 1, i + w)) {
                if (alpha[j] > 230 && alpha[i] > 230) { if (Math.abs(lum[i] - lum[j]) > 80) innerEdges++ }
                else if (alpha[j] < 60) outerEdges++
            }
        }
        // Anteil der helleren bzw. dunkleren Teilfläche: Ein kleines Detail (z. B. Play-Dreieck) reicht nicht.
        val mid = (lo + hi) / 2
        var darkPart = 0; var lightPart = 0
        for (v in 0..255) { if (v < mid) darkPart += hist[v] else lightPart += hist[v] }
        val minorShare = if (strong > 0) minOf(darkPart, lightPart).toDouble() / strong else 0.0
        val multiTone = allowGray && hi - lo > 80 && innerEdges > outerEdges * 0.4 && minorShare > 0.12
        // 3. Graustufen: einfarbige Logos werden reinweiß, mehrfarbige behalten ihre Abstufungen (hell bis mittelgrau).
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val g = if (multiTone) (150 + (lum[i] - lo).coerceIn(0, hi - lo) * 105 / (hi - lo)) else 255
            out[i] = Color.argb(alpha[i], g, g, g)
        }
        val ratio = covered.toDouble() / (w * h)
        if (maxX < 0 || ratio < 0.01 || ratio > 0.8) return null
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        // Logo zuschneiden und mittig in ein Quadrat setzen, damit alle Logos gleich groß wirken.
        val cw = maxX - minX + 1; val ch = maxY - minY + 1
        val crop = Bitmap.createBitmap(res, minX, minY, cw, ch)
        val pad = outH / 40
        val scale = minOf((outW - 2 * pad).toFloat() / cw, (outH - 2 * pad).toFloat() / ch)
        val sw = (cw * scale).toInt().coerceAtLeast(1); val sh = (ch * scale).toInt().coerceAtLeast(1)
        val outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(outBmp).drawBitmap(Bitmap.createScaledBitmap(crop, sw, sh, true), ((outW - sw) / 2).toFloat(), ((outH - sh) / 2).toFloat(), null)
        return outBmp
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }
}
