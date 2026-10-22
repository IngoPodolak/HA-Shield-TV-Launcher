package de.ingo.tvhome

import android.content.Context
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Einfache REST-Aufrufe an Home Assistant. */
object Ha {
    /** Die zuletzt funktionierende Adresse (lokal oder über die Domain). */
    @Volatile var activeUrl: String? = null

    fun get(ctx: Context, path: String): String? {
        val token = Config.haToken(ctx)
        val urls = Config.haUrls(ctx)
        if (urls.isEmpty() || token.isEmpty()) return null
        val ordered = (listOfNotNull(activeUrl?.takeIf { it in urls }) + urls).distinct()
        for (url in ordered) {
            try {
                val req = Request.Builder().url(url + path).header("Authorization", "Bearer $token").build()
                val body = TvHomeApp.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                if (body != null) { activeUrl = url; return body }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun state(ctx: Context, entityId: String): JSONObject? =
        get(ctx, "/api/states/$entityId")?.let { try { JSONObject(it) } catch (e: Exception) { null } }

    /** Kurzliste aller Entitäten für die Auswahl auf der Handy-Seite. */
    fun entitiesJson(ctx: Context): String? {
        val raw = get(ctx, "/api/states") ?: return null
        return try {
            val arr = JSONArray(raw)
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val id = s.getString("entity_id")
                val attr = s.optJSONObject("attributes") ?: JSONObject()
                out.put(
                    JSONObject().put("id", id)
                        .put("name", attr.optString("friendly_name", id))
                        .put("domain", id.substringBefore('.'))
                        .put("dc", attr.optString("device_class", ""))
                        .put("unit", attr.optString("unit_of_measurement", ""))
                        .put("state", s.optString("state"))
                )
            }
            out.toString()
        } catch (e: Exception) { null }
    }

    /** Adresse des Live-Bilds einer Kamera (ohne Anmeldung nutzbar, Zugangscode wechselt alle paar Minuten). */
    fun cameraUrl(ctx: Context, entityId: String, live: Boolean): String? {
        val s = state(ctx, entityId) ?: return null
        val token = s.optJSONObject("attributes")?.optString("access_token") ?: return null
        if (token.isEmpty()) return null
        val path = if (live) "camera_proxy_stream" else "camera_proxy"
        return (activeUrl ?: Config.haUrl(ctx)) + "/api/$path/$entityId?token=$token"
    }
}
