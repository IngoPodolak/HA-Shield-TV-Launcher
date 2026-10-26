package de.ingo.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Nachrichten als Popup am Fernseher.
 * - Vom Handy über die Einrichtungsseite (Port 8765, mit PIN)
 * - Aus Home Assistant über die offizielle Integration „Notifications for Android TV / Fire TV“
 *   (HA Shield TV Launcher spricht deren Protokoll auf Port 7676)
 */
object Notify {
    const val NFA_PORT = 7676
    @Volatile private var started = false
    /** true = HA Shield TV Launcher empfängt Nachrichten aus Home Assistant; false = Anschluss von einer anderen App belegt. */
    @Volatile var nfaReady = false

    /** Zeigt eine Nachricht. [image] darf null sein. [seconds] <= 0 = Standard aus den Einstellungen. */
    fun show(ctx: Context, title: String, message: String, image: ByteArray?, seconds: Int) {
        val cfg = Config.get(ctx)
        if (!cfg.optBoolean("msgEnabled", true)) return
        val secs = (if (seconds > 0) seconds else cfg.optInt("msgSeconds", 15)).coerceIn(3, 600)
        val file = image?.let { saveImage(ctx, it) }
        Overlay.showMessage(ctx, title.take(120), message.take(2000), file, secs)
    }

    /** Bild verkleinern (max. 1600 px) und als JPEG im Cache ablegen. */
    private fun saveImage(ctx: Context, data: ByteArray): String? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
            val scale = minOf(1.0, 1600.0 / maxOf(bmp.width, bmp.height))
            val out = if (scale < 1.0) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val dir = File(ctx.cacheDir, "notify").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "img_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    /** Bild von einer Adresse laden (z. B. Kamera-Schnappschuss aus Home Assistant). */
    fun fetchImage(ctx: Context, url: String): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val full = if (url.startsWith("/")) Config.haUrl(ctx) + url else url
            val b = Request.Builder().url(full)
            if (full.startsWith(Config.haUrl(ctx)) && Config.haToken(ctx).isNotEmpty()) b.header("Authorization", "Bearer " + Config.haToken(ctx))
            TvHomeApp.http.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (e: Exception) { null }
    }

    // ------------------------------------------------------------ Server für die HA-Integration (Port 7676)

    fun startNfaServer(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        // Ist der Anschluss belegt (z. B. durch die App „Notifications for Android TV“), alle 20 s neu versuchen.
        Thread {
            while (true) {
                try {
                    ServerSocket().use { server ->
                        server.reuseAddress = true
                        server.bind(java.net.InetSocketAddress(NFA_PORT))
                        nfaReady = true
                        while (true) {
                            val s = server.accept()
                            Thread { handle(app, s) }.start()
                        }
                    }
                } catch (e: Exception) {
                    nfaReady = false
                }
                try { Thread.sleep(20_000) } catch (_: InterruptedException) {}
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readLine(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 16_000) return null
        }
    }

    private fun handle(ctx: Context, sock: Socket) {
        sock.use { s ->
            try {
                s.soTimeout = 20_000
                val inp = BufferedInputStream(s.getInputStream())
                val first = readLine(inp) ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(inp) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceAtMost(30_000_000)
                val body = ByteArray(len)
                var read = 0
                while (read < len) { val n = inp.read(body, read, len - read); if (n < 0) break; read += n }

                if (first.startsWith("POST")) {
                    val parts = parseMultipart(headers["content-type"] ?: "", body)
                    val title = parts["title"]?.let { String(it.second, Charsets.UTF_8) } ?: ""
                    val msg = parts["msg"]?.let { String(it.second, Charsets.UTF_8) } ?: ""
                    val dur = parts["duration"]?.let { String(it.second).trim().toIntOrNull() } ?: 0
                    // "filename" ist das kleine Symbol (immer dabei), "filename2" das eigentliche Bild
                    val img = parts["filename2"]?.second?.takeIf { it.size > 100 }
                    if (msg.isNotBlank() || title.isNotBlank() || img != null) show(ctx, title, msg, img, dur)
                }
                val resp = "OK".toByteArray()
                val out = s.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${resp.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(resp); out.flush()
            } catch (_: Exception) {}
        }
    }

    /** Sehr einfacher Multipart-Parser: Feldname -> (Dateiname, Inhalt). */
    private fun parseMultipart(contentType: String, body: ByteArray): Map<String, Pair<String?, ByteArray>> {
        val result = HashMap<String, Pair<String?, ByteArray>>()
        val b = Regex("boundary=\"?([^\";]+)\"?").find(contentType)?.groupValues?.get(1) ?: return result
        val delim = ("--$b").toByteArray()
        var pos = indexOf(body, delim, 0)
        while (pos >= 0) {
            val partStart = pos + delim.size
            if (partStart + 2 <= body.size && body[partStart] == '-'.code.toByte() && body[partStart + 1] == '-'.code.toByte()) break
            val next = indexOf(body, delim, partStart)
            if (next < 0) break
            val part = body.copyOfRange(partStart, next)
            val sep = indexOf(part, "\r\n\r\n".toByteArray(), 0)
            if (sep > 0) {
                val head = String(part, 0, sep, Charsets.UTF_8)
                var content = part.copyOfRange(sep + 4, part.size)
                if (content.size >= 2 && content[content.size - 2] == '\r'.code.toByte() && content[content.size - 1] == '\n'.code.toByte())
                    content = content.copyOfRange(0, content.size - 2)
                val name = Regex("name=\"([^\"]*)\"").find(head)?.groupValues?.get(1)
                val fname = Regex("filename=\"([^\"]*)\"").find(head)?.groupValues?.get(1)
                if (name != null) result[name] = Pair(fname, content)
            }
            pos = next
        }
        return result
    }

    private fun indexOf(data: ByteArray, pat: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }
}
