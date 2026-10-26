package de.ingo.tvhome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Läuft dauerhaft im Hintergrund, hält eine Verbindung zu Home Assistant
 * und zeigt beim Klingeln das Kamera-Popup über jeder App.
 */
class HaService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var generation = 0
    private var retryDelay = 5_000L
    private var connectedKey = ""
    private val configListener: () -> Unit = { main.post { reconnectIfChanged() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        Config.addListener(configListener)
        connect()
        Cinema.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Config.removeListener(configListener)
        generation++
        ws?.close(1000, null)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("ha", "Home Assistant", NotificationManager.IMPORTANCE_MIN)
            )
        }
        return NotificationCompat.Builder(this, "ha")
            .setContentTitle("HA Shield TV Launcher")
            .setContentText("Verbunden mit Home Assistant")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun currentKey(): String {
        val c = Config.get(this)
        return listOf(Config.haUrls(this).joinToString(","), c.optString("haToken"), c.optString("doorbellEntity")).joinToString("|")
    }

    private fun reconnectIfChanged() {
        if (currentKey() != connectedKey) {
            retryDelay = 5_000L
            connect()
        }
    }

    private fun connect() {
        val gen = ++generation
        ws?.close(1000, null)
        ws = null
        connectedKey = currentKey()
        val urls = Config.haUrls(this)
        val token = Config.haToken(this)
        val doorbell = Config.get(this).optString("doorbellEntity", "")
        if (urls.isEmpty() || token.isEmpty() || doorbell.isEmpty()) return
        val url = urls[urlIndex % urls.size]
        var authed = false

        val wsUrl = url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        val req = try { Request.Builder().url(wsUrl).build() } catch (e: Exception) { return }
        ws = TvHomeApp.http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                val msg = try { JSONObject(text) } catch (e: Exception) { return }
                when (msg.optString("type")) {
                    "auth_required" -> webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                    "auth_ok" -> {
                        retryDelay = 5_000L
                        authed = true
                        failedInRow = 0
                        Ha.activeUrl = url
                        val trigger = JSONObject().put("platform", "state").put("entity_id", JSONArray().put(doorbell))
                        webSocket.send(
                            JSONObject().put("id", 1).put("type", "subscribe_trigger").put("trigger", trigger).toString()
                        )
                    }
                    "event" -> handleTrigger(msg.optJSONObject("event"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = scheduleRetry(gen, authed, urls.size)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = scheduleRetry(gen, authed, urls.size)
        })
    }

    private var urlIndex = 0
    private var failedInRow = 0

    /** Erst die anderen Adressen durchprobieren, danach immer länger warten. */
    private fun scheduleRetry(gen: Int, wasAuthed: Boolean, urlCount: Int) {
        if (gen != generation) return
        if (!wasAuthed) { urlIndex++; failedInRow++ }
        val delay = if (!wasAuthed && failedInRow < urlCount) 1_000L else retryDelay
        if (failedInRow >= urlCount) { failedInRow = 0; retryDelay = (retryDelay * 2).coerceAtMost(120_000L) }
        main.postDelayed({ if (gen == generation) connect() }, delay)
    }

    private fun handleTrigger(event: JSONObject?) {
        val trig = event?.optJSONObject("variables")?.optJSONObject("trigger") ?: return
        val to = trig.optJSONObject("to_state") ?: return
        val from = trig.optJSONObject("from_state")
        val entity = to.optString("entity_id")
        val newS = to.optString("state")
        val oldS = from?.optString("state") ?: ""
        if (newS == "unavailable" || newS == "unknown" || newS == oldS) return
        val domain = entity.substringBefore('.')
        val ringing = when (domain) {
            "binary_sensor", "input_boolean", "switch" -> newS == "on"
            else -> from != null
        }
        if (!ringing) return
        ring()
    }

    /** Wird auch vom „Popup testen“-Knopf aufgerufen. */
    fun ring() = ringNow(this)

    companion object {
        fun ringNow(ctx: android.content.Context) {
            val cfg = Config.get(ctx)
            if (!cfg.optBoolean("popupEnabled", true)) return
            val cam = cfg.optString("cameraEntity", "")
            Media.pauseIfPlaying(ctx)
            Thread {
                val camUrl = if (cam.isNotEmpty()) Ha.cameraUrl(ctx, cam, cfg.optString("popupCam", "live") == "live") else null
                Overlay.show(ctx, camUrl, cfg.optString("popupTitle", "Es klingelt an der Haustür"))
            }.start()
        }
    }
}
