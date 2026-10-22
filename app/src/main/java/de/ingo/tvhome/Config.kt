package de.ingo.tvhome

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * Alle Einstellungen liegen als ein JSON-Objekt in den SharedPreferences.
 * Die Oberfläche (JS) und die Handy-Einrichtungsseite lesen und schreiben dasselbe Objekt.
 */
object Config {
    private const val PREFS = "tvhome"
    private const val KEY = "config"
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    @Synchronized
    fun get(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return try { if (raw != null) JSONObject(raw) else JSONObject() } catch (e: Exception) { JSONObject() }
    }

    /** Übernimmt alle Schlüssel aus [patch] in die gespeicherte Konfiguration. */
    @Synchronized
    fun merge(ctx: Context, patch: JSONObject) {
        val cfg = get(ctx)
        val it = patch.keys()
        while (it.hasNext()) {
            val k = it.next()
            cfg.put(k, patch.get(k))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cfg.toString()).apply()
        listeners.forEach { l -> try { l() } catch (_: Exception) {} }
    }

    fun haUrl(ctx: Context): String = get(ctx).optString("haUrl", "").trim().trimEnd('/')
    fun haToken(ctx: Context): String = get(ctx).optString("haToken", "").trim()

    /** Vierstellige PIN, die die Handy-Einrichtungsseite absichert. */
    fun pin(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var pin = p.getString("pin", null)
        if (pin == null) {
            pin = (1000 + Random.nextInt(9000)).toString()
            p.edit().putString("pin", pin).apply()
        }
        return pin
    }
}
