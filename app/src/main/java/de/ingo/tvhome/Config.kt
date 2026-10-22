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

    /** Voreinstellungen für Ingos Haus. Alles lässt sich im Menü oder am Handy ändern. */
    private val DEFAULTS = JSONObject()
        .put("haUrl", "http://192.168.0.5")
        .put("haUrlAlt", org.json.JSONArray().put("https://homeassistant.podolan.de").put("http://homeassistant.local"))
        .put("lightEntity", "light.wohnzimmer_licht")
        .put("cameraEntity", "camera.reolink_doorbell_fliessend")
        .put("doorbellEntity", "binary_sensor.reolink_doorbell_besucher")
        .put("dishEntity", "sensor.siemens_dishwasher_verbleibende_programmlaufzeit")
        .put("dishStartEntity", "sensor.siemens_dishwasher_start_in")
        .put("ovenEntity", "sensor.bosch_384050532704003972_001_bsh_common_option_remainingprogramtime")
        .put("fanEntity", "fan.deckenventilator")
        .put("fanName", "Ventilator")
        .put("lightName", "Wohnzimmer")
        .put("cameraName", "Haustür")

    @Synchronized
    fun get(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val stored = try { if (raw != null) JSONObject(raw) else JSONObject() } catch (e: Exception) { JSONObject() }
        val cfg = JSONObject(DEFAULTS.toString())
        val it = stored.keys()
        while (it.hasNext()) { val k = it.next(); cfg.put(k, stored.get(k)) }
        return cfg
    }

    /** Hauptadresse zuerst, dann die Ausweichadressen. */
    fun haUrls(ctx: Context): List<String> {
        val c = get(ctx)
        val out = ArrayList<String>()
        val main = c.optString("haUrl", "").trim().trimEnd('/')
        if (main.isNotEmpty()) out.add(main)
        val alt = c.optJSONArray("haUrlAlt")
        if (alt != null) for (i in 0 until alt.length()) {
            val u = alt.optString(i).trim().trimEnd('/')
            if (u.isNotEmpty() && u !in out) out.add(u)
        }
        return out
    }

    /** Übernimmt alle Schlüssel aus [patch] in die gespeicherte Konfiguration. */
    @Synchronized
    fun merge(ctx: Context, patch: JSONObject) {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val cfg = try { if (raw != null) JSONObject(raw) else JSONObject() } catch (e: Exception) { JSONObject() }
        val it = patch.keys()
        while (it.hasNext()) {
            val k = it.next()
            cfg.put(k, patch.get(k))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cfg.toString()).apply()
        listeners.forEach { l -> try { l() } catch (_: Exception) {} }
    }

    fun haUrl(ctx: Context): String = Ha.activeUrl ?: haUrls(ctx).firstOrNull() ?: ""
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
