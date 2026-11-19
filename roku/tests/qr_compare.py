"""Vergleicht die QR-Matrizen aus BrightScript (brs-Ausgabe auf stdin) mit der Python-Referenz und dekodiert sie mit OpenCV."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from qr_ref import matrix
import numpy as np, cv2
tests = ["https://homeassistant.podolan.de/beatify/play?game=PzFtu9ClQh0", "http://192.168.0.5/beatify/play?game=rdAtgKlPPws", "hi", "https://example.com/" + "x" * 150, "https://homeassistant.podolan.de/beatify/play?game=abcdefghijk&lang=de-very-long-parameter-value-123456"]
out = sys.stdin.read().splitlines()
mats, cur = [], None
for l in out:
    l = l.strip()
    if l == "BEGIN": cur = []
    elif l == "END": mats.append(cur); cur = None
    elif cur is not None and l: cur.append([c == "1" for c in l])
assert len(mats) == len(tests), f"{len(mats)} Matrizen statt {len(tests)}:\n" + "\n".join(out[:40])
det = cv2.QRCodeDetector(); ok = True
for t, m in zip(tests, mats):
    ref = matrix(t)
    same = ref == m
    n = len(m); s = 10; q = 4
    img = np.full(((n + 2 * q) * s, (n + 2 * q) * s), 255, np.uint8)
    for y in range(n):
        for x in range(n):
            if m[y][x]: img[(y + q) * s:(y + q + 1) * s, (x + q) * s:(x + q + 1) * s] = 0
    val, _, _ = det.detectAndDecode(img)
    print(f"{n}x{n} gleich={same} lesbar={val == t}")
    ok = ok and same and val == t
sys.exit(0 if ok else 1)
