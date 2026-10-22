package de.ingo.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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

    /** PNG des App-Symbols ([banner] = breites TV-Banner statt Symbol). */
    fun iconPng(ctx: Context, pkg: String, banner: Boolean): ByteArray? {
        val key = "$pkg|$banner"
        iconCache[key]?.let { return it }
        val pm = ctx.packageManager
        val d: Drawable? = try {
            if (banner) {
                val i = pm.getLeanbackLaunchIntentForPackage(pkg)
                val ri = if (i != null) pm.resolveActivity(i, 0) else null
                ri?.activityInfo?.loadBanner(pm) ?: pm.getApplicationBanner(pkg)
            } else {
                pm.getApplicationIcon(pkg)
            }
        } catch (e: Exception) { null }
        if (d == null) return null
        val w = if (banner) 320 else 192
        val h = if (banner) 180 else 192
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        iconCache[key] = bytes
        return bytes
    }

    fun iconDataUrl(ctx: Context, pkg: String, banner: Boolean): String {
        val png = iconPng(ctx, pkg, banner) ?: return ""
        return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }
}
