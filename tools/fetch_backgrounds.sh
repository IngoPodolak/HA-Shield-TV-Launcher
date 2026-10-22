#!/usr/bin/env bash
# Lädt die eingebauten Hintergrundfotos herunter (wird von GitHub Actions beim Bauen ausgeführt).
set -u
DEST="app/src/main/assets/backgrounds"
mkdir -p "$DEST/thumbs"
LIST="["
n=0
while read -r id; do
  [[ -z "$id" || "$id" == \#* ]] && continue
  [[ $n -ge 20 ]] && break
  num=$(printf "%02d" $((n + 1)))
  url="https://images.unsplash.com/${id}?w=2560&q=80&fm=jpg&fit=crop&crop=entropy&ar=16:9"
  if curl -sfL --max-time 60 -o "$DEST/$num.jpg" "$url" && file "$DEST/$num.jpg" | grep -qi jpeg \
     && [[ $(stat -c%s "$DEST/$num.jpg") -gt 60000 ]]; then
    convert "$DEST/$num.jpg" -resize 480x270^ -gravity center -extent 480x270 -quality 75 "$DEST/thumbs/$num.jpg"
    [[ $n -gt 0 ]] && LIST+=","
    LIST+="{\"id\":\"$num\",\"name\":\"Landschaft $((n + 1))\",\"src\":\"$id\"}"
    n=$((n + 1))
    echo "OK   $num  $id"
  else
    rm -f "$DEST/$num.jpg"
    echo "FAIL      $id"
  fi
done < tools/backgrounds.txt
LIST+="]"
echo "$LIST" > "$DEST/list.json"
echo "Eingebaut: $n Fotos"
