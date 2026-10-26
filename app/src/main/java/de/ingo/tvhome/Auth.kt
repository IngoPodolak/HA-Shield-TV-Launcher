package de.ingo.tvhome

import android.content.Context
import android.net.Uri
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Anmeldung mit dem normalen Home-Assistant-Login (Benutzername + Passwort).
 * Danach erzeugt HA Shield TV Launcher selbst ein dauerhaftes Zugriffstoken – niemand muss ein Token abtippen.
 */
object Auth {
    /** Adresse der Home-Assistant-Anmeldeseite. [origin] ist z. B. http://127.0.0.1:8765 */
    fun authorizeUrl(ctx: Context, haUrl: String, origin: String): String {
        val state = Config.pin(ctx) + "|" + haUrl
        return haUrl.trimEnd('/') + "/auth/authorize?response_type=code" +
            "&client_id=" + Uri.encode("$origin/") +
            "&redirect_uri=" + Uri.encode("$origin/auth-callback") +
            "&state=" + Uri.encode(state)
    }

    /** Tauscht den Anmelde-Code gegen ein dauerhaftes Token und speichert es. Gibt eine Fehlermeldung oder null zurück. */
    fun finish(ctx: Context, haUrl: String, code: String, clientId: String): String? {
        val access = try {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", clientId)
                .build()
            TvHomeApp.http.newCall(Request.Builder().url(haUrl + "/auth/token").post(body).build()).execute().use { r ->
                if (!r.isSuccessful) return "Anmeldung abgelehnt (${r.code})"
                JSONObject(r.body!!.string()).getString("access_token")
            }
        } catch (e: Exception) {
            return "Home Assistant nicht erreichbar: ${e.message}"
        }

        val latch = CountDownLatch(1)
        var token: String? = null
        var error: String? = null
        val wsUrl = haUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        val name = "HA Shield TV Launcher Shield " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).format(Date())
        val ws = TvHomeApp.http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = try { JSONObject(text) } catch (e: Exception) { return }
                when (m.optString("type")) {
                    "auth_required" -> webSocket.send(JSONObject().put("type", "auth").put("access_token", access).toString())
                    "auth_ok" -> webSocket.send(
                        JSONObject().put("id", 1).put("type", "auth/long_lived_access_token")
                            .put("client_name", name).put("lifespan", 3650).toString()
                    )
                    "auth_invalid" -> { error = "Anmeldung ungültig"; latch.countDown() }
                    "result" -> {
                        if (m.optBoolean("success")) token = m.optString("result")
                        else error = m.optJSONObject("error")?.optString("message") ?: "Token konnte nicht erstellt werden"
                        latch.countDown()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                error = "Verbindung fehlgeschlagen: ${t.message}"; latch.countDown()
            }
        })
        latch.await(20, TimeUnit.SECONDS)
        ws.close(1000, null)
        val t = token ?: return error ?: "Zeitüberschreitung"

        // Die Adresse, über die die Anmeldung geklappt hat, wird zur Hauptadresse.
        val urls = Config.haUrls(ctx).filter { it != haUrl }
        Config.merge(
            ctx, JSONObject().put("haToken", t).put("haUrl", haUrl)
                .put("haUrlAlt", org.json.JSONArray(urls))
        )
        Ha.activeUrl = haUrl
        return null
    }
}
