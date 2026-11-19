sub Main()
    tests = ["https://homeassistant.podolan.de/beatify/play?game=PzFtu9ClQh0", "http://192.168.0.5/beatify/play?game=rdAtgKlPPws", "hi", "https://example.com/" + String(150, "x"), "https://homeassistant.podolan.de/beatify/play?game=abcdefghijk&lang=de-very-long-parameter-value-123456"]
    for each t in tests
        mat = QrMatrix(t)
        print "BEGIN"
        for each row in mat
            line = ""
            for each v in row
                if v then line = line + "1" else line = line + "0"
            end for
            print line
        end for
        print "END"
    end for
end sub
