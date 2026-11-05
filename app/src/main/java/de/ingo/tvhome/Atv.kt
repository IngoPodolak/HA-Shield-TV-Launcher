package de.ingo.tvhome

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Fernbedienung über den eingebauten „Android TV Remote Service“ (derselbe Weg wie die Google-TV-App am Handy).
 * Braucht kein Debugging und bleibt nach einem Neustart erhalten. Einmalig koppeln: Der Fernseher zeigt
 * einen 6-stelligen Code, der auf der Handy-Seite eingegeben wird.
 * Protokoll wie in der Bibliothek androidtvremote2 (Ports 6467 = Koppeln, 6466 = Fernbedienung).
 */
object Atv {
    private const val PAIR_PORT = 6467
    private const val API_PORT = 6466
    // PING | KEY | IME | POWER | VOLUME | APP_LINK
    private const val FEATURES = 1L or 2L or 4L or 32L or 64L or 512L

    @Volatile var lastError = ""
    private var pairSock: SSLSocket? = null
    private var api: SSLSocket? = null
    private var apiOut: OutputStream? = null
    @Volatile private var started = false
    @Volatile private var imeCounter = 0L
    @Volatile private var fieldCounter = 0L
    private val lock = Object()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("atv", Context.MODE_PRIVATE)
    fun isPaired(ctx: Context) = prefs(ctx).getBoolean("paired", false)

    // ------------------------------------------------------------ Protobuf (nur das Nötigste)

    class Pb {
        private val b = ByteArrayOutputStream()
        private fun varint(v: Long) { var x = v; while (x and 0x7FL.inv() != 0L) { b.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }; b.write(x.toInt()) }
        fun int(f: Int, v: Long) = apply { varint((f shl 3).toLong()); varint(v) }
        fun bytes(f: Int, d: ByteArray) = apply { varint(((f shl 3) or 2).toLong()); varint(d.size.toLong()); b.write(d) }
        fun str(f: Int, s: String) = bytes(f, s.toByteArray())
        fun msg(f: Int, m: Pb) = bytes(f, m.toByteArray())
        fun toByteArray(): ByteArray = b.toByteArray()
    }

    /** Feldnummer -> Wert (Long für Zahlen, ByteArray für Text/Unternachrichten). */
    private fun parse(d: ByteArray): Map<Int, Any> {
        val r = HashMap<Int, Any>()
        var i = 0
        fun rv(): Long { var s = 0; var v = 0L; while (true) { val c = d[i++].toInt() and 0xFF; v = v or ((c and 0x7F).toLong() shl s); if (c < 0x80) return v; s += 7 } }
        while (i < d.size) {
            val tag = rv(); val f = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> r[f] = rv()
                2 -> { val n = rv().toInt(); r[f] = d.copyOfRange(i, i + n); i += n }
                5 -> i += 4
                1 -> i += 8
                else -> return r
            }
        }
        return r
    }

    private fun send(out: OutputStream, data: ByteArray) {
        synchronized(out) {
            val len = ByteArrayOutputStream()
            var x = data.size
            while (x >= 0x80) { len.write((x and 0x7F) or 0x80); x = x ushr 7 }
            len.write(x)
            out.write(len.toByteArray() + data); out.flush()
        }
    }

    private fun recv(inp: InputStream): ByteArray {
        var s = 0; var n = 0
        while (true) { val c = inp.read(); if (c < 0) throw EOFException(); n = n or ((c and 0x7F) shl s); if (c < 0x80) break; s += 7 }
        val buf = ByteArray(n); var got = 0
        while (got < n) { val k = inp.read(buf, got, n - got); if (k < 0) throw EOFException(); got += k }
        return buf
    }

    // ------------------------------------------------------------ Schlüssel und Zertifikat

    private fun material(ctx: Context): Pair<PrivateKey, X509Certificate> {
        val p = prefs(ctx)
        val k = p.getString("key", null); val c = p.getString("cert", null)
        if (k != null && c != null) {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(android.util.Base64.decode(k, 0)))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(android.util.Base64.decode(c, 0).inputStream()) as X509Certificate
            return key to cert
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSigned(kp.public.encoded, kp.private, "atvremote")
        p.edit().putString("key", android.util.Base64.encodeToString(kp.private.encoded, 2))
            .putString("cert", android.util.Base64.encodeToString(cert.encoded, 2)).apply()
        return kp.private to cert
    }

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val n = content.size
        val len = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + len + content
    }
    private fun seq(vararg p: ByteArray) = der(0x30, p.fold(ByteArray(0)) { a, b -> a + b })

    /** Minimales selbst signiertes X.509-Zertifikat (SHA256withRSA), ohne Zusatzbibliothek. */
    private fun selfSigned(spki: ByteArray, priv: PrivateKey, cn: String): X509Certificate {
        val algId = seq(der(0x06, byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)), der(0x05, ByteArray(0)))
        val name = seq(der(0x31, seq(der(0x06, byteArrayOf(0x55, 0x04, 0x03)), der(0x0C, cn.toByteArray()))))
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val validity = seq(der(0x17, fmt.format(Date(now - 86_400_000L)).toByteArray()), der(0x17, fmt.format(Date(now + 10L * 365 * 86_400_000L)).toByteArray()))
        val serial = der(0x02, BigInteger(63, SecureRandom()).add(BigInteger.ONE).toByteArray())
        val tbs = seq(der(0xA0, der(0x02, byteArrayOf(2))), serial, algId, name, validity, name, spki)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(priv); update(tbs); sign() }
        val cert = seq(tbs, algId, der(0x03, byteArrayOf(0) + sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(cert.inputStream()) as X509Certificate
    }

    private fun open(ctx: Context, port: Int): SSLSocket {
        val (key, cert) = material(ctx)
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setKeyEntry("c", key, CharArray(0), arrayOf(cert)) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trustAll), SecureRandom()) }
        var last: Exception? = null
        for (host in listOfNotNull("127.0.0.1", Net.localIp()).distinct()) {
            try {
                val s = ssl.socketFactory.createSocket() as SSLSocket
                s.connect(InetSocketAddress(host, port), 3000)
                s.soTimeout = 10_000
                s.startHandshake()
                return s
            } catch (e: Exception) { last = e }
        }
        throw last ?: Exception("keine Verbindung")
    }

    // ------------------------------------------------------------ Koppeln

    private fun outer() = Pb().int(1, 2).int(2, 200)
    private fun recvOuter(s: SSLSocket, want: Int): Map<Int, Any> {
        val m = parse(recv(s.inputStream))
        if ((m[2] as? Long ?: 200L) != 200L) throw Exception("Fernseher meldet Fehler ${m[2]}")
        if (!m.containsKey(want)) throw Exception("unerwartete Antwort")
        return m
    }

    /** Startet das Koppeln: Danach zeigt der Fernseher einen 6-stelligen Code. */
    fun startPairing(ctx: Context): Boolean = synchronized(lock) {
        try {
            try { pairSock?.close() } catch (_: Exception) {}
            val s = open(ctx, PAIR_PORT); pairSock = s
            s.soTimeout = 15_000
            send(s.outputStream, outer().msg(10, Pb().str(1, "atvremote").str(2, "HA Shield TV Launcher")).toByteArray())
            recvOuter(s, 11)
            send(s.outputStream, outer().msg(20, Pb().msg(1, Pb().int(1, 3).int(2, 6)).int(3, 1)).toByteArray())
            recvOuter(s, 20)
            send(s.outputStream, outer().msg(30, Pb().msg(1, Pb().int(1, 3).int(2, 6)).int(2, 1)).toByteArray())
            recvOuter(s, 31)
            s.soTimeout = 0
            lastError = ""
            true
        } catch (e: Exception) {
            lastError = "Koppeln nicht möglich: ${e.message}"
            false
        }
    }

    private fun hexBytes(h: String): ByteArray {
        val s = if (h.length % 2 == 1) "0$h" else h
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Code vom Fernseher eingeben und Koppeln abschließen. */
    fun finishPairing(ctx: Context, code: String): Boolean = synchronized(lock) {
        val c = code.trim().uppercase()
        val s = pairSock
        try {
            if (s == null || s.isClosed) throw Exception("bitte zuerst „Koppeln“ starten")
            if (!Regex("[0-9A-F]{6}").matches(c)) throw Exception("Der Code hat 6 Zeichen (Ziffern und A–F)")
            val client = material(ctx).second.publicKey as RSAPublicKey
            val server = (s.session.peerCertificates[0] as X509Certificate).publicKey as RSAPublicKey
            val md = MessageDigest.getInstance("SHA-256")
            md.update(hexBytes(client.modulus.toString(16)))
            md.update(hexBytes("0" + client.publicExponent.toString(16)))
            md.update(hexBytes(server.modulus.toString(16)))
            md.update(hexBytes("0" + server.publicExponent.toString(16)))
            md.update(hexBytes(c.substring(2)))
            val h = md.digest()
            if ((h[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) throw Exception("Code stimmt nicht – bitte genau abtippen")
            s.soTimeout = 15_000
            send(s.outputStream, outer().msg(40, Pb().bytes(1, h)).toByteArray())
            recvOuter(s, 41)
            prefs(ctx).edit().putBoolean("paired", true).apply()
            try { s.close() } catch (_: Exception) {}
            pairSock = null
            closeApi()
            lastError = ""
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Koppeln fehlgeschlagen"
            false
        }
    }

    // ------------------------------------------------------------ Fernbedienung

    private fun closeApi() {
        try { api?.close() } catch (_: Exception) {}
        api = null; apiOut = null; started = false
    }

    private fun ensureApi(ctx: Context): OutputStream? {
        synchronized(lock) {
            val o = apiOut
            if (o != null && api?.isClosed == false && started) return o
            closeApi()
            val s = try { open(ctx, API_PORT) } catch (e: Exception) {
                lastError = "Fernbedienung nicht erreichbar – eventuell neu koppeln"
                return null
            }
            s.soTimeout = 0
            api = s; val out = s.outputStream; apiOut = out
            Thread {
                try {
                    val inp = s.inputStream
                    while (true) {
                        val m = parse(recv(inp))
                        when {
                            m.containsKey(1) -> send(out, Pb().msg(1, Pb().int(1, FEATURES)
                                .msg(2, Pb().int(3, 1).str(4, "1").str(5, "atvremote").str(6, "1.0.0"))).toByteArray())
                            m.containsKey(2) -> send(out, Pb().msg(2, Pb().int(1, FEATURES)).toByteArray())
                            m.containsKey(8) -> {
                                val v = parse(m[8] as ByteArray)[1] as? Long ?: 0L
                                send(out, Pb().msg(9, Pb().int(1, v)).toByteArray())
                            }
                            m.containsKey(21) -> {
                                val e = parse(m[21] as ByteArray)
                                imeCounter = e[1] as? Long ?: imeCounter
                                fieldCounter = e[2] as? Long ?: fieldCounter
                            }
                            m.containsKey(40) -> { started = true; synchronized(lock) { (lock as Object).notifyAll() } }
                        }
                    }
                } catch (_: Exception) {
                    synchronized(lock) { if (api === s) { api = null; apiOut = null; started = false } }
                }
            }.apply { isDaemon = true }.start()
            val until = System.currentTimeMillis() + 4000
            while (!started && System.currentTimeMillis() < until && api === s) (lock as Object).wait(200)
            if (!started) {
                closeApi()
                lastError = "Der Fernseher antwortet nicht – eventuell neu koppeln"
                return null
            }
            return out
        }
    }

    /** Taste senden (Android-Tastencode). */
    fun key(ctx: Context, code: Int): Boolean {
        for (attempt in 0..1) {
            val out = ensureApi(ctx) ?: return false
            try { send(out, Pb().msg(10, Pb().int(1, code.toLong()).int(2, 3)).toByteArray()); lastError = ""; return true }
            catch (e: Exception) { synchronized(lock) { closeApi() } }
        }
        return false
    }

    /** Text in das aktive Eingabefeld schreiben. */
    fun text(ctx: Context, text: String): Boolean {
        val out = ensureApi(ctx) ?: return false
        val p = (text.length - 1).toLong()
        return try {
            send(out, Pb().msg(21, Pb().int(1, imeCounter).int(2, fieldCounter)
                .msg(3, Pb().int(1, 1).msg(2, Pb().int(1, p).int(2, p).str(3, text)))).toByteArray())
            true
        } catch (e: Exception) { false }
    }
}
