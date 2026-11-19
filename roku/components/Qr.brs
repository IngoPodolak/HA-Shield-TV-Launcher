' QR-Code-Erzeugung (Byte-Modus, Fehlerkorrektur M, Version 1-10) – ohne Zusatzbibliothek.
' Aufbau nach ISO/IEC 18004; Maskenwahl wie in der verbreiteten JS-Bibliothek "qrcode".
' QrMatrix(text) liefert ein Array von Zeilen (Arrays aus Boolean, true = dunkel) oder invalid.

function QrXor(a as Integer, b as Integer) as Integer
    return (a or b) and (not (a and b))
end function

function QrGfMul(x as Integer, y as Integer) as Integer
    z = 0
    for i = 7 to 0 step -1
        z = QrXor((z << 1) and &h1FF, (z >> 7) * &h11D)
        if ((y >> i) and 1) = 1 then z = QrXor(z, x)
    end for
    return z and &hFF
end function

function QrRsDivisor(degree as Integer) as Object
    res = []
    for i = 1 to degree - 1
        res.Push(0)
    end for
    res.Push(1)
    root = 1
    for i = 0 to degree - 1
        for j = 0 to degree - 1
            res[j] = QrGfMul(res[j], root)
            if j + 1 < degree then res[j] = QrXor(res[j], res[j + 1])
        end for
        root = QrGfMul(root, 2)
    end for
    return res
end function

function QrRsRemainder(data as Object, divisor as Object) as Object
    n = divisor.Count()
    res = []
    for i = 0 to n - 1
        res.Push(0)
    end for
    for each b in data
        factor = QrXor(b, res[0])
        res.Shift()
        res.Push(0)
        for i = 0 to n - 1
            res[i] = QrXor(res[i], QrGfMul(divisor[i], factor))
        end for
    end for
    return res
end function

' Tabelle für Fehlerkorrektur M: [EC-Codewörter je Block, Blöcke Gruppe 1, Daten je Block G1, Blöcke G2, Daten je Block G2]
function QrTable(v as Integer) as Object
    t = [invalid, [10, 1, 16, 0, 0], [16, 1, 28, 0, 0], [26, 1, 44, 0, 0], [18, 2, 32, 0, 0], [24, 2, 43, 0, 0], [16, 4, 27, 0, 0], [18, 4, 31, 0, 0], [22, 2, 38, 2, 39], [22, 3, 36, 2, 37], [26, 4, 43, 1, 44]]
    return t[v]
end function

function QrAlign(v as Integer) as Object
    t = [invalid, [], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34], [6, 22, 38], [6, 24, 42], [6, 26, 46], [6, 28, 50]]
    return t[v]
end function

function QrUtf8(text as String) as Object
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(text)
    out = []
    for i = 0 to ba.Count() - 1
        out.Push(ba[i])
    end for
    return out
end function

function QrMatrix(text as String) as Object
    bytes = QrUtf8(text)
    ' kleinste passende Version suchen
    ver = 0
    for v = 1 to 10
        t = QrTable(v)
        dataCw = t[1] * t[2] + t[3] * t[4]
        cc = 8
        if v >= 10 then cc = 16
        if 4 + cc + bytes.Count() * 8 <= dataCw * 8 then
            ver = v
            exit for
        end if
    end for
    if ver = 0 then return invalid

    t = QrTable(ver)
    dataCw = t[1] * t[2] + t[3] * t[4]
    cc = 8
    if ver >= 10 then cc = 16

    ' Bitfolge
    bits = []
    QrAddBits(bits, 4, 4)
    QrAddBits(bits, bytes.Count(), cc)
    for each b in bytes
        QrAddBits(bits, b, 8)
    end for
    cap = dataCw * 8
    term = cap - bits.Count()
    if term > 4 then term = 4
    for i = 1 to term
        bits.Push(0)
    end for
    while (bits.Count() mod 8) <> 0
        bits.Push(0)
    end while
    pad = &hEC
    while bits.Count() < cap
        QrAddBits(bits, pad, 8)
        if pad = &hEC then pad = &h11 else pad = &hEC
    end while
    data = []
    for i = 0 to dataCw - 1
        cw = 0
        for j = 0 to 7
            cw = (cw << 1) or bits[i * 8 + j]
        end for
        data.Push(cw)
    end for

    ' Blöcke + Fehlerkorrektur, dann verschränken
    ecLen = t[0]
    divisor = QrRsDivisor(ecLen)
    blocks = []
    eccs = []
    k = 0
    for g = 0 to 1
        nb = t[1 + g * 2]
        blen = t[2 + g * 2]
        for bi = 1 to nb
            blk = []
            for i = 0 to blen - 1
                blk.Push(data[k])
                k = k + 1
            end for
            blocks.Push(blk)
            eccs.Push(QrRsRemainder(blk, divisor))
        end for
    end for
    final = []
    maxLen = t[2]
    if t[4] > maxLen then maxLen = t[4]
    for i = 0 to maxLen - 1
        for each blk in blocks
            if i < blk.Count() then final.Push(blk[i])
        end for
    end for
    for i = 0 to ecLen - 1
        for each e in eccs
            final.Push(e[i])
        end for
    end for

    ' Matrix aufbauen
    size = ver * 4 + 17
    q = { size: size, mods: [], fn: [] }
    for y = 0 to size - 1
        r1 = []
        r2 = []
        for x = 0 to size - 1
            r1.Push(false)
            r2.Push(false)
        end for
        q.mods.Push(r1)
        q.fn.Push(r2)
    end for
    QrDrawFunction(q, ver)
    QrDrawCodewords(q, final)

    ' beste Maske nach Strafpunkten
    best = -1
    bestScore = 0
    for mask = 0 to 7
        QrApplyMask(q, mask)
        QrDrawFormat(q, mask)
        score = QrPenalty(q)
        if best < 0 or score < bestScore then
            best = mask
            bestScore = score
        end if
        QrApplyMask(q, mask)
    end for
    QrApplyMask(q, best)
    QrDrawFormat(q, best)
    return q.mods
end function

sub QrAddBits(bits as Object, value as Integer, count as Integer)
    for i = count - 1 to 0 step -1
        bits.Push((value >> i) and 1)
    end for
end sub

sub QrSetFn(q as Object, x as Integer, y as Integer, dark as Boolean)
    q.mods[y][x] = dark
    q.fn[y][x] = true
end sub

sub QrDrawFunction(q as Object, ver as Integer)
    size = q.size
    for i = 0 to size - 1
        QrSetFn(q, 6, i, (i mod 2) = 0)
        QrSetFn(q, i, 6, (i mod 2) = 0)
    end for
    QrFinder(q, 3, 3)
    QrFinder(q, size - 4, 3)
    QrFinder(q, 3, size - 4)
    al = QrAlign(ver)
    n = al.Count()
    for i = 0 to n - 1
        for j = 0 to n - 1
            skip = (i = 0 and j = 0) or (i = 0 and j = n - 1) or (i = n - 1 and j = 0)
            if not skip then
                for dy = -2 to 2
                    for dx = -2 to 2
                        d = Abs(dx)
                        if Abs(dy) > d then d = Abs(dy)
                        QrSetFn(q, al[i] + dx, al[j] + dy, d <> 1)
                    end for
                end for
            end if
        end for
    end for
    QrDrawFormat(q, 0)
    if ver >= 7 then
        rmd = ver
        for i = 1 to 12
            rmd = QrXor((rmd << 1), (rmd >> 11) * &h1F25)
        end for
        vbits = (ver << 12) or rmd
        for i = 0 to 17
            bit = ((vbits >> i) and 1) = 1
            a = size - 11 + (i mod 3)
            b = i \ 3
            QrSetFn(q, a, b, bit)
            QrSetFn(q, b, a, bit)
        end for
    end if
end sub

sub QrFinder(q as Object, cx as Integer, cy as Integer)
    for dy = -4 to 4
        for dx = -4 to 4
            d = Abs(dx)
            if Abs(dy) > d then d = Abs(dy)
            xx = cx + dx
            yy = cy + dy
            if xx >= 0 and xx < q.size and yy >= 0 and yy < q.size then
                QrSetFn(q, xx, yy, d <> 2 and d <> 4)
            end if
        end for
    end for
end sub

sub QrDrawFormat(q as Object, mask as Integer)
    size = q.size
    data = (0 << 3) or mask ' Fehlerkorrektur M = 00
    rmd = data
    for i = 1 to 10
        rmd = QrXor((rmd << 1), (rmd >> 9) * &h537)
    end for
    bits = QrXor((data << 10) or rmd, &h5412)
    for i = 0 to 5
        QrSetFn(q, 8, i, ((bits >> i) and 1) = 1)
    end for
    QrSetFn(q, 8, 7, ((bits >> 6) and 1) = 1)
    QrSetFn(q, 8, 8, ((bits >> 7) and 1) = 1)
    QrSetFn(q, 7, 8, ((bits >> 8) and 1) = 1)
    for i = 9 to 14
        QrSetFn(q, 14 - i, 8, ((bits >> i) and 1) = 1)
    end for
    for i = 0 to 7
        QrSetFn(q, size - 1 - i, 8, ((bits >> i) and 1) = 1)
    end for
    for i = 8 to 14
        QrSetFn(q, 8, size - 15 + i, ((bits >> i) and 1) = 1)
    end for
    QrSetFn(q, 8, size - 8, true)
end sub

sub QrDrawCodewords(q as Object, data as Object)
    size = q.size
    total = data.Count() * 8
    i = 0
    rcol = size - 1
    while rcol >= 1
        if rcol = 6 then rcol = 5
        for vert = 0 to size - 1
            for j = 0 to 1
                x = rcol - j
                upward = ((rcol + 1) and 2) = 0
                if upward then y = size - 1 - vert else y = vert
                if not q.fn[y][x] and i < total then
                    q.mods[y][x] = ((data[i >> 3] >> (7 - (i and 7))) and 1) = 1
                    i = i + 1
                end if
            end for
        end for
        rcol = rcol - 2
    end while
end sub

function QrMaskBit(mask as Integer, x as Integer, y as Integer) as Boolean
    if mask = 0 then return ((x + y) mod 2) = 0
    if mask = 1 then return (y mod 2) = 0
    if mask = 2 then return (x mod 3) = 0
    if mask = 3 then return ((x + y) mod 3) = 0
    if mask = 4 then return (((x \ 3) + (y \ 2)) mod 2) = 0
    if mask = 5 then return ((x * y) mod 2) + ((x * y) mod 3) = 0
    if mask = 6 then return ((((x * y) mod 2) + ((x * y) mod 3)) mod 2) = 0
    return ((((x + y) mod 2) + ((x * y) mod 3)) mod 2) = 0
end function

sub QrApplyMask(q as Object, mask as Integer)
    size = q.size
    for y = 0 to size - 1
        row = q.mods[y]
        frow = q.fn[y]
        for x = 0 to size - 1
            if not frow[x] and QrMaskBit(mask, x, y) then row[x] = not row[x]
        end for
    end for
end sub

' Strafpunkte wie in der JS-Bibliothek "qrcode" (N1=3, N2=3, N3=40, N4=10)
function QrPenalty(q as Object) as Integer
    size = q.size
    m = q.mods
    pts = 0
    ' Regel 1: Läufe gleicher Farbe (Zeilen und Spalten)
    for r = 0 to size - 1
        sameR = 0
        sameC = 0
        lastR = invalid
        lastC = invalid
        for c = 0 to size - 1
            v = m[r][c]
            if lastR <> invalid and v = lastR then
                sameR = sameR + 1
            else
                if sameR >= 5 then pts = pts + 3 + (sameR - 5)
                lastR = v
                sameR = 1
            end if
            v = m[c][r]
            if lastC <> invalid and v = lastC then
                sameC = sameC + 1
            else
                if sameC >= 5 then pts = pts + 3 + (sameC - 5)
                lastC = v
                sameC = 1
            end if
        end for
        if sameR >= 5 then pts = pts + 3 + (sameR - 5)
        if sameC >= 5 then pts = pts + 3 + (sameC - 5)
    end for
    ' Regel 2: 2x2-Blöcke
    p2 = 0
    for r = 0 to size - 2
        for c = 0 to size - 2
            s = 0
            if m[r][c] then s = s + 1
            if m[r][c + 1] then s = s + 1
            if m[r + 1][c] then s = s + 1
            if m[r + 1][c + 1] then s = s + 1
            if s = 4 or s = 0 then p2 = p2 + 1
        end for
    end for
    pts = pts + p2 * 3
    ' Regel 3: suchmusterähnliche Folgen 1011101 mit 4 hellen Modulen
    p3 = 0
    for r = 0 to size - 1
        bitsR = 0
        bitsC = 0
        for c = 0 to size - 1
            b = 0
            if m[r][c] then b = 1
            bitsR = ((bitsR << 1) and &h7FF) or b
            if c >= 10 and (bitsR = &h5D0 or bitsR = &h05D) then p3 = p3 + 1
            b = 0
            if m[c][r] then b = 1
            bitsC = ((bitsC << 1) and &h7FF) or b
            if c >= 10 and (bitsC = &h5D0 or bitsC = &h05D) then p3 = p3 + 1
        end for
    end for
    pts = pts + p3 * 40
    ' Regel 4: Anteil dunkler Module
    dark = 0
    for r = 0 to size - 1
        for c = 0 to size - 1
            if m[r][c] then dark = dark + 1
        end for
    end for
    pct = (dark * 100#) / (size * size)
    k5 = pct / 5
    kc = Int(k5)
    if kc < k5 then kc = kc + 1
    k = Abs(kc - 10)
    pts = pts + k * 10
    return pts
end function
