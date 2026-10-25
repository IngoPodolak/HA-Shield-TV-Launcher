package de.ingo.tvhome

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Kleiner Webserver auf dem Shield (Port 8765). Darüber wird TV Home bequem
 * vom Handy aus eingerichtet: Home-Assistant-Zugang, Geräte, Lieblings-Apps, eigene Fotos.
 */
object SetupServer {
    const val PORT = 8765
    @Volatile private var started = false
    private lateinit var app: Context

    fun url(ctx: Context): String {
        val ip = Net.localIp() ?: "?"
        return "http://$ip:$PORT/?pin=" + Config.pin(ctx)
    }

    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        Thread {
            try {
                ServerSocket(PORT).use { server ->
                    while (true) {
                        val s = server.accept()
                        Thread { handle(s) }.start()
                    }
                }
            } catch (e: Exception) {
                started = false
            }
        }.apply { isDaemon = true }.start()
    }

    private class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray)

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

    private fun parse(inp: InputStream): Req? {
        val first = readLine(inp) ?: return null
        val parts = first.split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(inp) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 40_000_000) return null
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = inp.read(body, read, len - read)
            if (n < 0) break
            read += n
        }
        val uri = Uri.parse("http://x" + parts[1])
        val q = HashMap<String, String>()
        for (k in uri.queryParameterNames) q[k] = uri.getQueryParameter(k) ?: ""
        return Req(parts[0].uppercase(), uri.path ?: "/", q, headers, body)
    }

    private fun send(out: OutputStream, code: Int, type: String, data: ByteArray) {
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; 502 -> "Bad Gateway"; else -> "Error" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(data)
        out.flush()
    }

    private fun json(out: OutputStream, code: Int, s: String) = send(out, code, "application/json; charset=utf-8", s.toByteArray())

    private fun handle(s: Socket) {
        s.use { sock ->
            try {
                sock.soTimeout = 30_000
                val inp = BufferedInputStream(sock.getInputStream())
                val out = sock.getOutputStream()
                val r = parse(inp) ?: return
                val pinOk = (r.query["pin"] ?: r.headers["x-pin"]) == Config.pin(app)

                if (r.path == "/" || r.path == "/setup") {
                    val html = app.assets.open("web/setup.html").readBytes()
                    return send(out, 200, "text/html; charset=utf-8", html)
                }
                if (r.path == "/auth-callback") {
                    val state = r.query["state"] ?: ""
                    val pin = state.substringBefore('|')
                    val haUrl = state.substringAfter('|', "").trimEnd('/')
                    val code = r.query["code"] ?: ""
                    val err = when {
                        pin != Config.pin(app) -> "PIN passt nicht. Bitte die Anmeldung neu starten."
                        code.isEmpty() || haUrl.isEmpty() -> "Die Anmeldung wurde abgebrochen."
                        else -> Auth.finish(app, haUrl, code, "http://" + (r.headers["host"] ?: "127.0.0.1:$PORT") + "/")
                    }
                    return send(out, 200, "text/html; charset=utf-8", resultPage(err).toByteArray())
                }
                if (!pinOk) return json(out, 403, """{"error":"PIN falsch. Bitte den QR-Code am Fernseher neu scannen."}""")

                when {
                    r.path == "/api/state" -> {
                        val cfg = Config.get(app)
                        val hasToken = cfg.optString("haToken").isNotEmpty()
                        cfg.remove("haToken")
                        cfg.put("hasToken", hasToken)
                        val apps = JSONArray()
                        for (a in Apps.list(app)) apps.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
                        val res = JSONObject().put("config", cfg).put("apps", apps)
                            .put("haUrls", JSONArray(Config.haUrls(app)))
                            .put("version", BuildConfig.VERSION_NAME)
                            .put("overlay", Overlay.canShow(app))
                        json(out, 200, res.toString())
                    }
                    r.path == "/api/config" && r.method == "POST" -> {
                        val patch = JSONObject(String(r.body))
                        if (patch.has("haToken") && patch.optString("haToken").isBlank()) patch.remove("haToken")
                        if (patch.has("haUrl")) patch.put("haUrl", patch.optString("haUrl").trim().trimEnd('/'))
                        Config.merge(app, patch)
                        json(out, 200, """{"ok":true}""")
                    }
                    r.path == "/api/entities" -> {
                        val e = Ha.entitiesJson(app)
                        if (e == null) json(out, 502, """{"error":"Home Assistant nicht erreichbar oder Token ungültig."}""")
                        else json(out, 200, e)
                    }
                    r.path == "/api/icon" -> {
                        val png = Apps.iconPng(app, r.query["pkg"] ?: "", "color")
                        if (png == null) send(out, 404, "text/plain", ByteArray(0)) else send(out, 200, "image/png", png)
                    }
                    r.path == "/api/backgrounds" -> json(out, 200, Backgrounds.customJson(app))
                    r.path == "/api/logo" && r.method == "POST" -> {
                        val pkg = r.query["pkg"] ?: ""
                        if (pkg.isEmpty() || r.body.isEmpty()) return json(out, 400, """{"error":"Datei oder App fehlt"}""")
                        if (r.body.size > 8_000_000) return json(out, 400, """{"error":"Datei zu groß (max. 8 MB)"}""")
                        try { Logos.save(app, pkg, r.query["name"] ?: "logo.png", r.body) }
                        catch (e: Exception) { return json(out, 400, JSONObject().put("error", e.message ?: "Bild unbrauchbar").toString()) }
                        json(out, 200, """{"ok":true}""")
                    }
                    r.path == "/api/logo/delete" && r.method == "POST" -> {
                        Logos.delete(app, r.query["pkg"] ?: "")
                        json(out, 200, """{"ok":true}""")
                    }
                    r.path == "/api/logo" -> {
                        val f = Logos.file(app, r.query["pkg"] ?: "")
                        if (f == null) send(out, 404, "text/plain", ByteArray(0))
                        else send(out, 200, if (f.extension == "svg") "image/svg+xml" else "image/" + f.extension, f.readBytes())
                    }
                    r.path == "/api/background" && r.method == "POST" -> {
                        if (r.body.isEmpty()) return json(out, 400, """{"error":"Leere Datei"}""")
                        val name = Backgrounds.saveCustom(app, r.query["name"] ?: "foto", r.body)
                        Config.merge(app, JSONObject().put("bgChanged", System.currentTimeMillis()))
                        json(out, 200, JSONObject().put("ok", true).put("file", name).toString())
                    }
                    r.path == "/api/background/delete" && r.method == "POST" -> {
                        Backgrounds.deleteCustom(app, r.query["file"] ?: "")
                        Config.merge(app, JSONObject().put("bgChanged", System.currentTimeMillis()))
                        json(out, 200, """{"ok":true}""")
                    }
                    r.path.startsWith("/bg/") -> {
                        val f = File(Backgrounds.customDir(app), File(r.path.removePrefix("/bg/")).name)
                        if (!f.exists()) send(out, 404, "text/plain", ByteArray(0)) else send(out, 200, "image/jpeg", f.readBytes())
                    }
                    r.path == "/api/key" && r.method == "POST" -> {
                        val ok = Remote.key(app, r.query["k"] ?: "")
                        json(out, if (ok) 200 else 400, JSONObject().put("ok", ok).put("error", Remote.lastError).toString())
                    }
                    r.path == "/api/text" && r.method == "POST" -> {
                        val ok = Remote.text(app, String(r.body, Charsets.UTF_8))
                        json(out, 200, JSONObject().put("ok", ok).toString())
                    }
                    r.path == "/api/remote-test" && r.method == "POST" -> {
                        val ok = Remote.test(app)
                        json(out, 200, JSONObject().put("ok", ok).put("error", Remote.lastError).toString())
                    }
                    r.path == "/api/backgrounds/builtin" -> {
                        val arr = JSONArray(Backgrounds.builtinJson(app))
                        val outArr = JSONArray()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            outArr.put(JSONObject().put("id", o.optString("id")).put("name", o.optString("name")))
                        }
                        json(out, 200, outArr.toString())
                    }
                    r.path == "/api/bgthumb" -> {
                        val id = (r.query["id"] ?: "").filter { it.isLetterOrDigit() }
                        val bytes = try { app.assets.open("backgrounds/thumbs/$id.jpg").readBytes() } catch (e: Exception) {
                            try { app.assets.open("backgrounds/$id.jpg").readBytes() } catch (e2: Exception) { null }
                        }
                        if (bytes == null) send(out, 404, "text/plain", ByteArray(0)) else send(out, 200, "image/jpeg", bytes)
                    }
                    r.path == "/api/test-popup" && r.method == "POST" -> {
                        HaService.ringNow(app)
                        json(out, 200, JSONObject().put("ok", true).put("overlay", Overlay.canShow(app)).toString())
                    }
                    else -> json(out, 404, """{"error":"Nicht gefunden"}""")
                }
            } catch (e: Exception) {
                try { json(sock.getOutputStream(), 500, JSONObject().put("error", e.message ?: "Fehler").toString()) } catch (_: Exception) {}
            }
        }
    }

    private fun resultPage(err: String?): String {
        val ok = err == null
        val title = if (ok) "TVHOME_OK" else "TVHOME_FAIL"
        val head = if (ok) "Angemeldet" else "Anmeldung fehlgeschlagen"
        val text = if (ok) "TV Home ist jetzt mit Home Assistant verbunden. Du kannst dieses Fenster schließen."
        else (err ?: "") + " Bitte nochmal versuchen."
        val esc = text.replace("&", "&amp;").replace("<", "&lt;")
        return """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title><style>body{font:18px/1.5 system-ui,Roboto,sans-serif;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#111;color:#f2f2f2;text-align:center;padding:24px}
            h1{font-size:28px;font-weight:600;margin:0 0 8px;color:${if (ok) "#4fc483" else "#ff6b5e"}}</style></head>
            <body><div><h1>$head</h1><p>$esc</p></div></body></html>"""
    }

    @Suppress("unused")
    private fun readAll(inp: InputStream): ByteArray {
        val bos = ByteArrayOutputStream(); inp.copyTo(bos); return bos.toByteArray()
    }
}
