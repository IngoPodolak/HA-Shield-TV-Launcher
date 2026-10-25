package de.ingo.tvhome

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors

/**
 * Fernbedienung vom Handy aus.
 * Steuerkreuz, Zurück, Home usw. funktionieren in allen Apps über das Netzwerk-Debugging des Shield
 * (TV Home verbindet sich mit sich selbst; beim ersten Mal fragt der Fernseher einmal nach Erlaubnis).
 * Wiedergabe-Tasten gehen direkt und ohne Verzögerung.
 */
object Remote {
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var dadb: Dadb? = null
    @Volatile var lastError: String = ""

    private val KEYS = mapOf(
        "up" to KeyEvent.KEYCODE_DPAD_UP, "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT, "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER, "back" to KeyEvent.KEYCODE_BACK,
        "home" to KeyEvent.KEYCODE_HOME, "menu" to KeyEvent.KEYCODE_MENU,
        "volup" to KeyEvent.KEYCODE_VOLUME_UP, "voldown" to KeyEvent.KEYCODE_VOLUME_DOWN,
        "mute" to KeyEvent.KEYCODE_VOLUME_MUTE, "settings" to KeyEvent.KEYCODE_SETTINGS,
        "search" to KeyEvent.KEYCODE_SEARCH, "power" to KeyEvent.KEYCODE_SLEEP
    )
    private val MEDIA = mapOf(
        "playpause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        "rewind" to KeyEvent.KEYCODE_MEDIA_REWIND, "forward" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        "next" to KeyEvent.KEYCODE_MEDIA_NEXT, "prev" to KeyEvent.KEYCODE_MEDIA_PREVIOUS
    )

    private fun conn(ctx: Context): Dadb {
        dadb?.let { return it }
        val dir = File(ctx.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey"); val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        val d = Dadb.create("127.0.0.1", 5555, AdbKeyPair.read(priv, pub))
        dadb = d
        return d
    }

    private fun shell(ctx: Context, cmd: String): Boolean {
        for (attempt in 0..1) {
            try {
                conn(ctx).shell(cmd)
                lastError = ""
                return true
            } catch (e: Throwable) {
                try { dadb?.close() } catch (_: Throwable) {}
                dadb = null
                lastError = "Keine Verbindung. Ist am Shield „Netzwerk-Debugging“ an und wurde „Zulassen“ bestätigt?"
            }
        }
        return false
    }

    /** Gibt true zurück, wenn der Befehl verstanden wurde (die Ausführung läuft im Hintergrund). */
    fun key(ctx: Context, name: String): Boolean {
        MEDIA[name]?.let { code ->
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
            return true
        }
        val code = KEYS[name] ?: return false
        io.execute { shell(ctx, "input keyevent $code") }
        return true
    }

    /** Text in das aktuell aktive Eingabefeld schreiben (z. B. Suche in Netflix). */
    fun text(ctx: Context, text: String): Boolean {
        val clean = text.take(200).replace("\n", " ")
        if (clean.isEmpty()) return false
        val escaped = clean.map { c ->
            when {
                c == ' ' -> "%s"
                c.isLetterOrDigit() && c.code < 128 -> c.toString()
                c in ".,-_@:/+" -> c.toString()
                else -> "\\" + c
            }
        }.joinToString("")
        io.execute { shell(ctx, "input text '$escaped'") }
        return true
    }

    /** Verbindung prüfen (löst beim ersten Mal die Abfrage am Fernseher aus). */
    fun test(ctx: Context): Boolean = shell(ctx, "echo ok")
}
