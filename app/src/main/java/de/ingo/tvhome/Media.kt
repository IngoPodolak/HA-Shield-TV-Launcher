package de.ingo.tvhome

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** Pausiert die laufende Wiedergabe (wie die Pause-Taste der Fernbedienung) und setzt sie bei Bedarf fort. */
object Media {
    @Volatile private var pausedByUs = false

    private fun send(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    /** Beim Klingeln: nur pausieren, wenn gerade wirklich etwas läuft. */
    fun pauseIfPlaying(ctx: Context) {
        val mode = Config.get(ctx).optString("ringPlayback", "pause")
        if (mode == "off") return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!am.isMusicActive) return
        try { send(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE); pausedByUs = true } catch (_: Exception) {}
    }

    /** Beim Schließen des Popups: fortsetzen, falls so eingestellt und wir pausiert haben. */
    fun resumeIfWanted(ctx: Context) {
        if (!pausedByUs) return
        pausedByUs = false
        if (Config.get(ctx).optString("ringPlayback", "pause") != "resume") return
        try { send(ctx, KeyEvent.KEYCODE_MEDIA_PLAY) } catch (_: Exception) {}
    }
}
