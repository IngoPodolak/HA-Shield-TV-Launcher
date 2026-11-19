' Hält die Live-Verbindung (WebSocket) zu Beatify und verbindet bei Abbruch neu.
' status: connecting | open | closed | unsupported

sub init()
    m.top.functionName = "run"
end sub

function wsOpen(url as String) as Object
    port = CreateObject("roMessagePort")
    ws = CreateObject("roWebSocket")
    if ws = invalid then return invalid
    ws.SetMessagePort(port)
    if LCase(Left(url, 4)) = "wss:" then
        ws.SetCertificatesFile("common:/certs/ca-bundle.crt")
        ws.InitClientCertificates()
    end if
    ws.SetUrl(url)
    m.top.unobserveField("outbox")
    m.top.unobserveField("url")
    m.top.observeField("outbox", port)
    m.top.observeField("url", port)
    ok = ws.Open()
    return { ws: ws, port: port, ok: ok }
end function

sub run()
    clock = CreateObject("roTimespan")
    idlePort = CreateObject("roMessagePort")
    m.top.observeField("url", idlePort)
    url = m.top.url
    conn = invalid
    connected = false
    retryAt = 0
    openedAt = 0
    lastPing = 0
    lastRx = 0
    while true
        now = clock.TotalMilliseconds()
        if conn = invalid and url <> "" and now >= retryAt then
            conn = wsOpen(url)
            if conn = invalid then
                m.top.status = "unsupported"
                return
            end if
            m.top.status = "connecting"
            openedAt = now
            if type(conn.ok) = "roBoolean" and not conn.ok then
                conn = invalid
                m.top.status = "closed"
                retryAt = now + 3000
            end if
        end if

        if conn <> invalid then
            msg = wait(500, conn.port)
        else
            m.top.unobserveField("url")
            m.top.observeField("url", idlePort)
            msg = wait(500, idlePort)
        end if
        now = clock.TotalMilliseconds()

        if msg <> invalid then
            t = type(msg)
            if t = "roWebSocketEvent" then
                et = msg.GetType()
                if et = 1 then
                    connected = true
                    m.top.status = "open"
                    lastRx = now
                    lastPing = now
                else if et = 5 then
                    info = msg.GetInfo()
                    if info <> invalid and info.Text <> invalid then
                        lastRx = now
                        m.top.inbox = info.Text
                    end if
                else if et = 2 then
                    connected = false
                    conn = invalid
                    m.top.status = "closed"
                    retryAt = now + 3000
                end if
            else if t = "roSGNodeEvent" then
                f = msg.GetField()
                if f = "outbox" then
                    if connected and conn <> invalid then conn.ws.Send(msg.GetData())
                else if f = "url" then
                    url = msg.GetData()
                    if conn <> invalid then conn.ws.Close()
                    conn = invalid
                    connected = false
                    retryAt = 0
                end if
            end if
        end if

        if connected and conn <> invalid then
            if now - lastPing > 20000 then
                conn.ws.Send(FormatJson({ type: "ping" }))
                lastPing = now
            end if
            if now - lastRx > 65000 then
                conn.ws.Close()
                conn = invalid
                connected = false
                m.top.status = "closed"
                retryAt = now + 1000
            end if
        else if conn <> invalid and now - openedAt > 15000 then
            conn.ws.Close()
            conn = invalid
            m.top.status = "closed"
            retryAt = now + 3000
        end if
    end while
end sub
