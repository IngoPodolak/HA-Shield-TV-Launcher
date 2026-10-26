# HA Shield TV Launcher – eigene Startseite für den NVIDIA Shield TV

Eine schlichte Startseite im Stil von Dock- und Listen-Layouts über schönen Landschaftsfotos, mit Home-Assistant-Anbindung:

- **Apps** als Dock (unten) oder als große Liste (links), Reihenfolge frei wählbar
- **Türkamera** live auf der Startseite, **Klingel-Popup** über jeder App (auch mitten im Film)
- **Wohnzimmerlicht**: an/aus, dimmen, Lichtfarbe (warm–kalt) und Farben
- **Deckenventilator**: an/aus und Stufe
- **Spülmaschine und Backofen**: Restlaufzeit und Endzeit, nur sichtbar wenn sie laufen
- **Optionen**: Layout, Material (klar, Milchglas, dunkel), Fokus-Stil, Uhr, Schriftgröße
- **Hintergrund**: 20 eingebaute Fotos, eigene Fotos vom Handy oder Bing Bild des Tages, fest oder wechselnd
- **Art Mode**: blendet nach einer Weile alles aus und zeigt nur das Foto und die Uhr

Bedienung: Steuerkreuz und OK. **OK gedrückt halten** öffnet Zusatzfunktionen (App verschieben oder entfernen, Licht direkt schalten).

## Installation (einmalig, vom PC aus)

1. Auf dem Shield: Einstellungen → Geräteeinstellungen → Info → 7× auf „Build“ tippen.
   Dann Geräteeinstellungen → Entwickleroptionen → **Netzwerk-Debugging** einschalten. Die IP-Adresse steht dort.
2. Auf dem PC die [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) entpacken
   und `tv-home.apk` aus den [Releases](../../releases/latest) in denselben Ordner legen.
3. Im Ordner ein Terminal öffnen und ausführen (IP anpassen; beim ersten Verbinden auf dem Fernseher „Zulassen“ wählen):

```
adb connect 192.168.1.50
adb install -r tv-home.apk
adb shell appops set de.ingo.tvhome SYSTEM_ALERT_WINDOW allow
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Die dritte Zeile erlaubt das Klingel-Popup. Die vierte macht HA Shield TV Launcher zur Startseite (die Original-Startseite wird nur deaktiviert, nicht gelöscht).

## Einrichten

Auf dem Fernseher: Einstellungen (Zahnrad) → **Am Handy einrichten** → QR-Code scannen.
Dort Home-Assistant-Adresse und Token eintragen, Geräte auswählen, Apps sortieren und eigene Fotos hochladen.

## Updates

Einstellungen → **Nach Update suchen**. Beim ersten Mal fragt Android, ob HA Shield TV Launcher Apps installieren darf.

## Zurück zur Original-Startseite

```
adb shell pm enable com.google.android.tvlauncher
adb uninstall de.ingo.tvhome
```
