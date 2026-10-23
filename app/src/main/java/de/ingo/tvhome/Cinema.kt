package de.ingo.tvhome

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.util.concurrent.Executors

/**
 * Kinolicht: Läuft eine Wiedergabe, gehen die gewählten Lampen aus. Bei Pause/Stopp gehen sie wieder an.
 * Kurze Unterbrechungen (Spulen, Laden) werden durch eine Wartezeit überbrückt.
 * Erkennung über den Audio-Status des Shield – funktioniert mit jeder App, ohne Zusatzrechte.
 */
object Cinema {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var started = false
    private var playingSince = 0L
    private var silentSince = 0L
    @Volatile private var lightsOff = false
    @Volatile private var busy = false
    private val turnedOff = ArrayList<String>()

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val tick = object : Runnable {
            override fun run() {
                try { tick(app) } catch (_: Exception) {}
                main.postDelayed(this, 1000)
            }
        }
        main.post(tick)
    }

    private fun lights(ctx: Context): List<String> {
        val arr = Config.get(ctx).optJSONArray("cinemaLights") ?: JSONArray()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.contains('.') }
    }

    private fun tick(ctx: Context) {
        val cfg = Config.get(ctx)
        val enabled = cfg.optBoolean("cinemaEnabled", true)
        val ids = lights(ctx)
        if (!enabled || ids.isEmpty()) {
            playingSince = 0; silentSince = 0
            if (lightsOff && !busy) restore(ctx)
            return
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = System.currentTimeMillis()
        if (am.isMusicActive) {
            silentSince = 0
            if (playingSince == 0L) playingSince = now
            val offDelay = cfg.optInt("cinemaOffDelay", 3) * 1000L
            if (!lightsOff && !busy && now - playingSince >= offDelay) turnOff(ctx, ids)
        } else {
            playingSince = 0
            if (silentSince == 0L) silentSince = now
            val onDelay = cfg.optInt("cinemaOnDelay", 8) * 1000L
            if (lightsOff && !busy && now - silentSince >= onDelay) restore(ctx)
        }
    }

    private fun turnOff(ctx: Context, ids: List<String>) {
        busy = true
        io.execute {
            try {
                val on = ids.filter { Ha.state(ctx, it)?.optString("state") == "on" }
                synchronized(turnedOff) { turnedOff.clear(); turnedOff.addAll(on) }
                if (on.isNotEmpty()) Ha.callService(ctx, "turn_off", on)
                lightsOff = true
            } finally { busy = false }
        }
    }

    private fun restore(ctx: Context) {
        busy = true
        io.execute {
            try {
                val ids = synchronized(turnedOff) { ArrayList(turnedOff).also { turnedOff.clear() } }
                if (ids.isNotEmpty()) Ha.callService(ctx, "turn_on", ids)
                lightsOff = false
            } finally { busy = false }
        }
    }
}
