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
        // Hintergrund schätzen: Bild in Felder teilen (Banner 16×9, Symbole 8×8) und je Feld die vorherrschende
        // Farbe (Median) messen. Der Bildrand gilt als Hintergrund. Von dort wächst der Hintergrund nach innen,
        // aber nur über weiche Übergänge (Verlauf, Lichtschein) – scharfe Sprünge (Rand eines Logo-Kastens) stoppen ihn.
        // Felder, die so nicht erreicht werden, bekommen den Wert ihrer Nachbarn.
        val gx = if (allowGray) 8 else 16; val gy = if (allowGray) 8 else 9
        val n = gx * gy
        val med = Array(n) { DoubleArray(3) }
        val valid = BooleanArray(n)
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
                med[k][0] = rs[rs.size / 2].toDouble(); med[k][1] = gs[gs.size / 2].toDouble(); med[k][2] = bs[bs.size / 2].toDouble()
                valid[k] = true; opaqueCells++
            }
        }
        // Undurchsichtiger Hintergrund nur, wenn fast alle Felder deckend sind (sonst hat das Bild schon Transparenz).
        val useBg = opaqueCells >= n * 0.85
        val cellVal = Array(n) { DoubleArray(3) }
        if (useBg) {
            val acc = BooleanArray(n)
            for (cy in 0 until gy) for (cx in 0 until gx) {
                val k = cy * gx + cx
                if (valid[k] && (cx == 0 || cy == 0 || cx == gx - 1 || cy == gy - 1)) { acc[k] = true; med[k].copyInto(cellVal[k]) }
            }
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
            var changed = true
            while (changed) {
                changed = false
                for (cy in 0 until gy) for (cx in 0 until gx) {
                    val k = cy * gx + cx
                    if (acc[k] || !valid[k]) continue
                    for (d in dirs) {
                        val nx = cx + d[0]; val ny = cy + d[1]
                        if (nx < 0 || ny < 0 || nx >= gx || ny >= gy) continue
                        val nk = ny * gx + nx
                        if (!acc[nk]) continue
                        val diff = Math.abs(med[k][0] - cellVal[nk][0]) + Math.abs(med[k][1] - cellVal[nk][1]) + Math.abs(med[k][2] - cellVal[nk][2])
                        if (diff < 60) { acc[k] = true; med[k].copyInto(cellVal[k]); changed = true; break }
                    }
                }
            }
            var filled = acc.copyOf()
            var guard = 0
            while (filled.any { !it } && guard++ < gx + gy) {
                val nf = filled.copyOf()
                for (cy in 0 until gy) for (cx in 0 until gx) {
                    val k = cy * gx + cx
                    if (filled[k]) continue
                    var r = 0.0; var g = 0.0; var b = 0.0; var c = 0
                    for (d in dirs) {
                        val nx = cx + d[0]; val ny = cy + d[1]
                        if (nx < 0 || ny < 0 || nx >= gx || ny >= gy) continue
                        val nk = ny * gx + nx
                        if (filled[nk]) { r += cellVal[nk][0]; g += cellVal[nk][1]; b += cellVal[nk][2]; c++ }
                    }
                    if (c > 0) { cellVal[k][0] = r / c; cellVal[k][1] = g / c; cellVal[k][2] = b / c; nf[k] = true }
                }
                filled = nf
            }
        }
        fun bgAt(x: Int, y: Int, out: DoubleArray) {
            val fx = (x.toDouble() / w * gx - 0.5).coerceIn(0.0, gx - 1.0)
            val fy = (y.toDouble() / h * gy - 0.5).coerceIn(0.0, gy - 1.0)
            val x0 = fx.toInt(); val y0 = fy.toInt(); val x1 = minOf(x0 + 1, gx - 1); val y1 = minOf(y0 + 1, gy - 1)
            val tx = fx - x0; val ty = fy - y0
            for (k in 0..2) {
                out[k] = (cellVal[y0 * gx + x0][k] * (1 - tx) + cellVal[y0 * gx + x1][k] * tx) * (1 - ty) +
                    (cellVal[y1 * gx + x0][k] * (1 - tx) + cellVal[y1 * gx + x1][k] * tx) * ty
            }
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
        // 3. Logo einheitlich reinweiß (Ausstanzen innerer Flächen hat echte Logos mit Schattierungen zerstört).
        val out = IntArray(w * h)
        for (i in 0 until w * h) out[i] = Color.argb(alpha[i], 255, 255, 255)
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
