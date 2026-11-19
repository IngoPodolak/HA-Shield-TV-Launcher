' Beatify TV für Roku – zeigt die TV-Ansicht des Beatify-Musikquiz und spielt auf Wunsch die Musik ab
' (in Beatify als Lautsprecher „TV-Bildschirm“ wählen).

sub init()
    m.C = {
        text: "#F2F2F7", muted: "#9A9AB0", dim: "#6B6B82", accent: "#22D3EE", pink: "#FF3D77",
        purple: "#8B5CF6", gold: "#FBBF24", green: "#34D399", panel: "#171A2E", panel2: "#1F2340", line: "#2A2F52"
    }
    m.view = m.top.findNode("view")
    m.conn = m.top.findNode("conn")
    m.music = m.top.findNode("music")
    m.voice = m.top.findNode("voice")
    m.music.observeField("state", "onMusicState")
    m.tick = m.top.findNode("tick")
    m.tick.observeField("fire", "onTick")
    m.tick.control = "start"

    m.state = invalid
    m.status = "idle"
    m.qrUrl = ""
    m.qrMat = invalid
    m.cdLabel = invalid
    m.cdClock = invalid
    m.remainMs = 0
    m.playId = invalid
    m.playReported = false
    m.songStopped = false
    m.lastRound = -1

    m.reg = CreateObject("roRegistrySection", "beatify")
    base = m.reg.Read("url")
    if base = "" then base = "https://homeassistant.podolan.de"
    m.base = normBase(base)

    m.ws = CreateObject("roSGNode", "WsTask")
    m.ws.observeField("inbox", "onInbox")
    m.ws.observeField("status", "onStatus")
    m.ws.url = wsUrl(m.base)
    m.ws.control = "run"

    renderConnecting()
    m.top.setFocus(true)
end sub

' ---------------------------------------------------------------- Adresse

function normBase(s as String) as String
    s = s.Trim()
    if s = "" then return ""
    if Instr(1, s, "://") = 0 then s = "https://" + s
    while Right(s, 1) = "/"
        s = Left(s, Len(s) - 1)
    end while
    return s
end function

function wsUrl(base as String) as String
    if base = "" then return ""
    if LCase(Left(base, 5)) = "https" then return "wss" + Mid(base, 6) + "/beatify/ws"
    return "ws" + Mid(base, 5) + "/beatify/ws"
end function

function hostOf(base as String) as String
    i = Instr(1, base, "://")
    if i > 0 then return Mid(base, i + 3)
    return base
end function

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "options" then
        showAddressDialog()
        return true
    end if
    return false
end function

sub showAddressDialog()
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    if dlg <> invalid then
        dlg.title = "Adresse von Home Assistant"
        dlg.message = ["z. B. https://homeassistant.podolan.de oder http://192.168.0.5"]
        dlg.text = m.base
        dlg.buttons = ["Speichern", "Abbrechen"]
        dlg.observeField("buttonSelected", "onDialogButton")
    else
        dlg = CreateObject("roSGNode", "KeyboardDialog")
        dlg.title = "Adresse von Home Assistant"
        dlg.text = m.base
        dlg.buttons = ["Speichern", "Abbrechen"]
        dlg.observeField("buttonSelected", "onDialogButton")
    end if
    m.dlg = dlg
    m.top.dialog = dlg
end sub

sub onDialogButton()
    dlg = m.dlg
    if dlg = invalid then return
    if dlg.buttonSelected = 0 then
        txt = ""
        if dlg.hasField("text") then txt = s2(dlg.text)
        if txt = "" and dlg.hasField("keyboard") then
            if dlg.keyboard <> invalid then txt = s2(dlg.keyboard.text)
        end if
        b = normBase(txt)
        if b <> "" then
            m.base = b
            m.reg.Write("url", b)
            m.reg.Flush()
            m.state = invalid
            m.ws.url = wsUrl(b)
            renderConnecting()
        end if
    end if
    dlg.close = true
    m.dlg = invalid
    m.top.setFocus(true)
end sub

' ---------------------------------------------------------------- Verbindung

sub send(obj as Object)
    m.ws.outbox = FormatJson(obj)
end sub

sub onStatus()
    m.status = m.ws.status
    host = hostOf(m.base)
    if m.status = "open" then
        m.conn.text = "Verbunden mit " + host
        send({ type: "get_state" })
        send({ type: "playback_register", role: "tv", claim: true })
    else if m.status = "connecting" then
        m.conn.text = "Verbinde mit " + host + " …"
    else if m.status = "unsupported" then
        m.conn.text = ""
        renderMessage("Roku-Software zu alt", "Dieser Roku kann keine Live-Verbindung aufbauen. Bitte unter Einstellungen → System → Systemaktualisierung die Software aktualisieren.")
    else
        m.conn.text = "Keine Verbindung zu " + host + " – neuer Versuch …"
        if m.state = invalid then renderConnecting()
    end if
end sub

sub onInbox()
    data = ParseJson(m.ws.inbox)
    if data = invalid or type(data) <> "roAssociativeArray" then return
    t = s2(data.type)
    if t = "state" then
        m.state = data
        render()
    else if t = "game_ended" then
        m.state = invalid
        stopAudio()
        renderNoGame()
    else if t = "error" then
        if s2(data.code) = "GAME_NOT_STARTED" then
            m.state = invalid
            renderNoGame()
        end if
    else if t = "game_starting" then
        renderMessage("Gleich geht's los", "Das Spiel startet – die erste Runde wird geladen …")
    else if t = "metadata_update" then
        if m.state <> invalid and data.song <> invalid then
            m.state.song = data.song
            render()
        end if
    else if t = "song_stopped" then
        m.songStopped = true
        if m.state <> invalid then render()
    else if t = "song_skipped" then
        m.songStopped = false
    else if t = "browser_audio" then
        handleAudio(data)
    else if t = "playback_released" then
        stopAudio()
    end if
end sub

' ---------------------------------------------------------------- Musik (Lautsprecher „TV-Bildschirm“)

function streamFormat(url as String) as String
    u = LCase(url)
    if Instr(1, u, ".m3u8") > 0 then return "hls"
    if Instr(1, u, ".m4a") > 0 or Instr(1, u, ".mp4") > 0 or Instr(1, u, ".aac") > 0 then return "mp4"
    if Instr(1, u, ".flac") > 0 then return "flac"
    if Instr(1, u, ".wav") > 0 then return "wav"
    return "mp3"
end function

sub report(state as String, id as Dynamic)
    f = { type: "playback_report", state: state }
    if id <> invalid then f.id = id
    send(f)
end sub

sub handleAudio(d as Object)
    a = s2(d.action)
    if a = "play" then
        url = s2(d.url)
        if LCase(Left(url, 8)) <> "https://" then
            report("error", d.id)
            return
        end if
        c = CreateObject("roSGNode", "ContentNode")
        c.url = url
        c.streamFormat = streamFormat(url)
        m.playId = d.id
        m.playReported = false
        m.music.control = "stop"
        m.music.content = c
        m.music.control = "play"
    else if a = "stop" then
        m.music.control = "pause"
        report("paused", invalid)
    else if a = "resume" then
        m.music.control = "resume"
    else if a = "announce" then
        url = s2(d.url)
        if url = "" then return
        c = CreateObject("roSGNode", "ContentNode")
        c.url = url
        c.streamFormat = streamFormat(url)
        m.voice.content = c
        m.voice.control = "play"
    end if
end sub

sub stopAudio()
    m.music.control = "stop"
    m.voice.control = "stop"
    m.playId = invalid
end sub

sub onMusicState()
    s = m.music.state
    if s = "playing" then
        if not m.playReported then
            m.playReported = true
            report("playing", m.playId)
        end if
    else if s = "finished" then
        report("ended", invalid)
    else if s = "error" then
        report("error", m.playId)
    end if
end sub

' ---------------------------------------------------------------- Bausteine

sub clearView()
    n = m.view.getChildCount()
    if n > 0 then m.view.removeChildrenIndex(n, 0)
    m.cdLabel = invalid
end sub

function fnt(size as Integer, bold as Boolean) as Object
    f = CreateObject("roSGNode", "Font")
    if bold then f.uri = "font:BoldSystemFontFile" else f.uri = "font:SystemFontFile"
    f.size = size
    return f
end function

function lbl(p as Object, x as Integer, y as Integer, w as Integer, text as String, size as Integer, color as String, bold as Boolean) as Object
    l = p.createChild("Label")
    l.translation = [x, y]
    l.width = w
    l.text = text
    l.color = color
    l.font = fnt(size, bold)
    return l
end function

function lblC(p as Object, x as Integer, y as Integer, w as Integer, text as String, size as Integer, color as String, bold as Boolean) as Object
    l = lbl(p, x, y, w, text, size, color, bold)
    l.horizAlign = "center"
    return l
end function

function lblW(p as Object, x as Integer, y as Integer, w as Integer, text as String, size as Integer, color as String, bold as Boolean) as Object
    l = lbl(p, x, y, w, text, size, color, bold)
    l.wrap = true
    return l
end function

function isTrue(v as Dynamic) as Boolean
    t = type(v)
    if t = "roBoolean" or t = "Boolean" then return v
    return false
end function

function isFalse(v as Dynamic) as Boolean
    t = type(v)
    if t = "roBoolean" or t = "Boolean" then return not v
    return false
end function

function card(p as Object, x as Integer, y as Integer, w as Integer, h as Integer, color as String, op as Float) as Object
    c = p.createChild("Poster")
    c.uri = "pkg:/images/round.9.png"
    c.translation = [x, y]
    c.width = w
    c.height = h
    c.blendColor = color
    c.opacity = op
    return c
end function

function pill(p as Object, x as Integer, y as Integer, text as String, color as String, textColor as String) as Integer
    size = 30
    w = Int(Len(text) * size * 0.68) + 64
    card(p, x, y, w, 64, color, 1.0)
    l = lbl(p, x, y + 13, w, text, size, textColor, true)
    l.horizAlign = "center"
    return w
end function

function s2(v as Dynamic) as String
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "roInteger" or t = "LongInteger" or t = "roLongInteger" then return v.ToStr()
    if t = "roFloat" or t = "Float" or t = "roDouble" or t = "Double" then return Int(v).ToStr()
    if t = "roBoolean" or t = "Boolean" then
        if v then return "true" else return "false"
    end if
    return ""
end function

function num(v as Dynamic) as Double
    if v = invalid then return 0
    t = type(v)
    if t = "roString" or t = "String" then return Val(v)
    if t = "roBoolean" or t = "Boolean" then return 0
    return v
end function

function absUrl(u as Dynamic) as String
    s = s2(u)
    if s = "" then return ""
    if Left(s, 1) = "/" then return m.base + s
    return s
end function

sub drawQr(p as Object, x as Integer, y as Integer, size as Integer, url as String)
    if url = "" then return
    if url <> m.qrUrl then
        m.qrUrl = url
        m.qrMat = QrMatrix(url)
    end if
    mat = m.qrMat
    if mat = invalid then return
    n = mat.Count()
    modSize = Int(size / (n + 4))
    if modSize < 2 then modSize = 2
    total = modSize * (n + 4)
    g = p.createChild("Group")
    g.translation = [x, y]
    bg = g.createChild("Rectangle")
    bg.width = total
    bg.height = total
    bg.color = "#FFFFFF"
    off = modSize * 2
    for r = 0 to n - 1
        row = mat[r]
        c = 0
        while c < n
            if row[c] then
                s = c
                while c < n and row[c]
                    c = c + 1
                end while
                rr = g.createChild("Rectangle")
                rr.translation = [off + s * modSize, off + r * modSize]
                rr.width = (c - s) * modSize
                rr.height = modSize
                rr.color = "#000000"
            else
                c = c + 1
            end if
        end while
    end for
end sub

function shortUrl(u as String) as String
    i = Instr(1, u, "://")
    if i > 0 then u = Mid(u, i + 3)
    return u
end function

function scoreMap() as Object
    sm = {}
    if m.state <> invalid and m.state.players <> invalid then
        for each p in m.state.players
            if p.name <> invalid then sm[p.name] = num(p.score)
        end for
    end if
    return sm
end function

sub header(p as Object, phaseText as String, phaseColor as String)
    lbl(p, 80, 56, 600, "BEATIFY", 40, m.C.accent, true)
    w = pill(p, 380, 46, phaseText, phaseColor, "#0B0B14")
    s = m.state
    if s <> invalid and s.round <> invalid and num(s.round) > 0 then
        rt = "RUNDE " + s2(s.round)
        if s.total_rounds <> invalid and num(s.total_rounds) > 0 then rt = rt + " VON " + s2(s.total_rounds)
        if isTrue(s.last_round) then rt = "FINALE · " + rt
        pill(p, 380 + w + 24, 46, rt, m.C.panel2, m.C.text)
    end if
end sub

' ---------------------------------------------------------------- Ansichten

sub renderConnecting()
    clearView()
    v = m.view
    lblC(v, 0, 360, 1920, "BEATIFY TV", 96, m.C.accent, true)
    lblC(v, 0, 500, 1920, "Verbinde mit " + hostOf(m.base) + " …", 40, m.C.text, false)
    t = lbl(v, 360, 580, 1200, "Mit der Taste * auf der Fernbedienung kannst du die Adresse von Home Assistant ändern.", 30, m.C.muted, false)
    t.horizAlign = "center"
    t.wrap = true
end sub

sub renderMessage(title as String, msg as String)
    clearView()
    v = m.view
    lbl(v, 80, 56, 600, "BEATIFY", 40, m.C.accent, true)
    lblC(v, 0, 400, 1920, title, 72, m.C.text, true)
    t = lbl(v, 360, 520, 1200, msg, 34, m.C.muted, false)
    t.horizAlign = "center"
    t.wrap = true
end sub

sub renderNoGame()
    renderMessage("Kein Spiel aktiv", "Starte am Handy ein Spiel in Beatify (Home Assistant → Beatify). Diese Anzeige folgt automatisch. Für Musik über diesen Fernseher wähle als Lautsprecher „TV-Bildschirm“.")
end sub

sub render()
    s = m.state
    if s = invalid then
        renderNoGame()
        return
    end if
    ph = s2(s.phase)
    if s.round <> invalid and num(s.round) <> m.lastRound then
        m.lastRound = num(s.round)
        m.songStopped = false
    end if
    if ph = "LOBBY" then
        renderLobby(s)
    else if ph = "PLAYING" then
        renderPlaying(s)
    else if ph = "REVEAL" then
        renderReveal(s)
    else if ph = "END" then
        renderEnd(s)
    else if ph = "PAUSED" then
        renderMessage("Spiel pausiert", "Der Gastgeber setzt das Spiel gleich fort.")
    else
        renderNoGame()
    end if
end sub

sub renderLobby(s as Object)
    clearView()
    v = m.view
    header(v, "WARTERAUM", m.C.accent)
    card(v, 80, 170, 760, 820, m.C.panel, 0.95)
    lblC(v, 80, 200, 760, "Scannen und mitspielen", 40, m.C.text, true)
    drawQr(v, 140, 270, 640, s2(s.join_url))
    u = lbl(v, 100, 930, 720, shortUrl(s2(s.join_url)), 24, m.C.muted, false)
    u.horizAlign = "center"

    card(v, 900, 170, 940, 820, m.C.panel, 0.95)
    cnt = 0
    if s.players <> invalid then cnt = s.players.Count()
    lbl(v, 950, 200, 840, "Mitspieler (" + cnt.ToStr() + ")", 40, m.C.text, true)
    if cnt = 0 then
        lblW(v, 950, 290, 840, "Noch niemand da – scanne den QR-Code mit dem Handy.", 30, m.C.muted, false)
    else
        i = 0
        for each p in s.players
            if i < 20 then
                col = i mod 2
                row = i \ 2
                x = 950 + col * 430
                y = 290 + row * 66
                card(v, x, y, 400, 54, m.C.panel2, 1.0)
                nm = lbl(v, x + 24, y + 9, 360, s2(p.name), 30, m.C.text, true)
                if isFalse(p.connected) then nm.color = m.C.dim
            end if
            i = i + 1
        end for
    end if
    lbl(v, 950, 930, 840, "Der Gastgeber startet das Spiel am Handy.", 28, m.C.muted, false)
end sub

sub renderPlaying(s as Object)
    clearView()
    v = m.view
    header(v, "RATEN", m.C.pink)

    ' Countdown
    card(v, 80, 170, 820, 640, m.C.panel, 0.95)
    lblC(v, 80, 210, 820, "Aus welchem Jahr ist der Song?", 40, m.C.text, true)
    m.cdLabel = lbl(v, 80, 300, 820, "", 300, m.C.accent, true)
    m.cdLabel.horizAlign = "center"
    lblC(v, 80, 680, 820, "Sekunden", 34, m.C.muted, false)
    yr = s.year_range
    if yr <> invalid and yr.min <> invalid and yr.max <> invalid then
        lblC(v, 80, 735, 820, "Zwischen " + s2(yr.min) + " und " + s2(yr.max), 30, m.C.dim, false)
    end if
    m.remainMs = 0
    if s.deadline <> invalid and s.server_now_ms <> invalid then
        m.remainMs = num(s.deadline) - num(s.server_now_ms)
    else if s.seconds_remaining <> invalid then
        m.remainMs = num(s.seconds_remaining) * 1000
    end if
    m.cdClock = CreateObject("roTimespan")
    updateCountdown()

    if m.songStopped or isTrue(s.song_stopped) then
        pill(v, 300, 840, "MUSIK GESTOPPT", m.C.gold, "#0B0B14")
    end if

    ' Wer hat getippt
    card(v, 960, 170, 880, 640, m.C.panel, 0.95)
    total = 0
    if s.players <> invalid then total = s.players.Count()
    lbl(v, 1010, 200, 780, s2(s.submitted_count) + " von " + total.ToStr() + " haben getippt", 40, m.C.text, true)
    i = 0
    if s.players <> invalid then
        for each p in s.players
            if i < 16 then
                col = i mod 2
                row = i \ 2
                x = 1010 + col * 400
                y = 280 + row * 64
                done = isTrue(p.submitted)
                if done then clr = m.C.green else clr = m.C.panel2
                card(v, x, y, 370, 52, clr, 1.0)
                if done then tc = "#0B0B14" else tc = m.C.muted
                lbl(v, x + 22, y + 9, 330, s2(p.name), 28, tc, true)
            end if
            i = i + 1
        end for
    end if

    ' später einsteigen
    if s.join_url <> invalid then
        card(v, 960, 840, 880, 170, m.C.panel, 0.95)
        drawQr(v, 980, 850, 150, s2(s.join_url))
        lbl(v, 1160, 880, 640, "Noch einsteigen?", 32, m.C.text, true)
        lbl(v, 1160, 926, 660, shortUrl(s2(s.join_url)), 22, m.C.muted, false)
    end if
end sub

sub updateCountdown()
    if m.cdLabel = invalid or m.cdClock = invalid then return
    remain = m.remainMs - m.cdClock.TotalMilliseconds()
    if remain < 0 then remain = 0
    secs = Int((remain + 999) / 1000)
    m.cdLabel.text = secs.ToStr()
    if secs <= 5 then m.cdLabel.color = m.C.pink else m.cdLabel.color = m.C.accent
end sub

sub onTick()
    updateCountdown()
end sub

function funFact(song as Object) as String
    if song = invalid then return ""
    ff = s2(song.fun_fact_de)
    if ff = "" then ff = s2(song.fun_fact)
    return ff
end function

sub renderReveal(s as Object)
    clearView()
    v = m.view
    header(v, "AUFLÖSUNG", m.C.accent)
    song = s.song

    ' Song
    card(v, 80, 170, 1180, 470, m.C.panel, 0.95)
    if song <> invalid then
        art = absUrl(song.album_art)
        if art <> "" then
            po = v.createChild("Poster")
            po.translation = [120, 210]
            po.width = 390
            po.height = 390
            po.loadDisplayMode = "scaleToFit"
            po.uri = art
        end if
        tl = lbl(v, 550, 210, 680, s2(song.title), 52, m.C.text, true)
        tl.wrap = true
        tl.maxLines = 2
        lbl(v, 550, 350, 680, s2(song.artist), 36, m.C.muted, false)
        lbl(v, 550, 400, 680, s2(song.year), 160, m.C.pink, true)
        ff = funFact(song)
        if ff <> "" then
            f = lbl(v, 550, 580, 680, ff, 22, m.C.dim, false)
            f.wrap = true
            f.maxLines = 2
        end if
    end if
    if isTrue(s.round_voided) then
        pill(v, 550, 590, "RUNDE ZÄHLT NICHT", m.C.gold, "#0B0B14")
    end if

    ' Tipps dieser Runde
    card(v, 80, 670, 1180, 340, m.C.panel, 0.95)
    lbl(v, 120, 690, 900, "Tipps dieser Runde", 32, m.C.text, true)
    if s.players <> invalid then
        ps = []
        for each p in s.players
            ps.Push(p)
        end for
        ps.SortBy("round_score", "r")
        i = 0
        for each p in ps
            if i < 8 then
                col = i \ 4
                row = i mod 4
                x = 120 + col * 570
                y = 745 + row * 62
                lbl(v, x, y, 230, s2(p.name), 28, m.C.text, true)
                if p.guess <> invalid then
                    gt = s2(p.guess)
                    off = num(p.years_off)
                    if off = 0 then gt = gt + "  genau!" else gt = gt + "  (±" + s2(off) + ")"
                    lbl(v, x + 240, y, 200, gt, 28, m.C.muted, false)
                else
                    lbl(v, x + 240, y, 200, "kein Tipp", 28, m.C.dim, false)
                end if
                pts = num(p.round_score)
                pl = lbl(v, x + 440, y, 100, "+" + s2(pts), 28, m.C.green, true)
                if pts <= 0 then pl.color = m.C.dim
            end if
            i = i + 1
        end for
    end if

    drawLeaderboard(v, s, 1300, 170, 540, 840, "Zwischenstand")
end sub

sub drawLeaderboard(v as Object, s as Object, x as Integer, y as Integer, w as Integer, h as Integer, title as String)
    card(v, x, y, w, h, m.C.panel, 0.95)
    lbl(v, x + 40, y + 30, w - 80, title, 36, m.C.text, true)
    sm = scoreMap()
    lb = s.leaderboard
    if lb = invalid then return
    i = 0
    for each e in lb
        if i < 10 then
            yy = y + 100 + i * 72
            rk = s2(e.rank)
            if rk = "" then rk = (i + 1).ToStr()
            rc = m.C.muted
            if rk = "1" then rc = m.C.gold
            lbl(v, x + 40, yy, 80, "#" + rk, 32, rc, true)
            lbl(v, x + 130, yy, w - 280, s2(e.name), 32, m.C.text, true)
            sc = e.score
            if sc = invalid and e.name <> invalid then sc = sm[e.name]
            sl = lbl(v, x + w - 150, yy, 110, s2(sc), 32, m.C.accent, true)
            sl.horizAlign = "right"
        end if
        i = i + 1
    end for
end sub

sub renderEnd(s as Object)
    clearView()
    v = m.view
    header(v, "SPIELENDE", m.C.gold)
    card(v, 80, 170, 1180, 840, m.C.panel, 0.95)
    lblC(v, 80, 260, 1180, "Gewonnen hat", 44, m.C.muted, false)
    wn = ""
    ws = ""
    if s.winner <> invalid then
        wn = s2(s.winner.name)
        ws = s2(s.winner.score)
        if isTrue(s.winner.is_tie) then lblC(v, 80, 320, 1180, "Unentschieden!", 36, m.C.gold, true)
    end if
    w = lbl(v, 120, 400, 1100, wn, 110, m.C.gold, true)
    w.horizAlign = "center"
    w.wrap = true
    w.maxLines = 2
    if ws <> "" then lblC(v, 80, 680, 1180, ws + " Punkte", 56, m.C.text, true)
    lblC(v, 80, 900, 1180, "Danke fürs Mitspielen!", 32, m.C.muted, false)
    drawLeaderboard(v, s, 1300, 170, 540, 840, "Endstand")
end sub
