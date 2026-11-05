@echo off
chcp 65001 >nul
title HA Shield TV Launcher installieren
setlocal

rem ---- Standard-Adresse (Shield TV); beim Start kann eine andere eingegeben werden
set SHIELD=192.168.0.158
set DIR=%LOCALAPPDATA%\TVHome
set ADB="%LOCALAPPDATA%\TVHome\platform-tools\adb.exe"

echo.
echo  ============================================
echo    HA Shield TV Launcher installieren
echo  ============================================
echo.

if not exist "%DIR%" mkdir "%DIR%"
cd /d "%DIR%"

if not exist "%DIR%\platform-tools\adb.exe" (
  echo [1/5] Lade Android-Werkzeuge von Google ...
  curl -sL -o pt.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip || goto fehler
  tar -xf pt.zip || goto fehler
  del pt.zip
) else (
  echo [1/5] Android-Werkzeuge sind schon da.
)

echo [2/5] Lade die neueste TV-Home-Version von GitHub ...
curl -sfL -o tv-home.apk https://github.com/IngoPodolak/HA-Shield-TV-Launcher/releases/latest/download/tv-home.apk || goto fehler

echo.
echo  Auf welchem Geraet soll installiert werden?
echo  IP-Adresse eingeben (z. B. Google-TV-Stick) oder nur Enter fuer den Shield (%SHIELD%).
echo  Die IP steht am Fernseher unter Einstellungen - Netzwerk und Internet - (dein WLAN).
set /p DEV="  IP-Adresse: "
if not "%DEV%"=="" set SHIELD=%DEV%
set T=%SHIELD%:5555
echo.
echo [3/5] Verbinde mit %SHIELD% ...
echo.
echo   WICHTIG: Auf dem Fernseher erscheint jetzt eventuell die Frage
echo   "USB-Debugging zulassen?". Dort "Immer von diesem Computer zulassen"
echo   ankreuzen und OK druecken.
echo.
%ADB% disconnect >nul 2>&1
%ADB% connect %T% >nul 2>&1
set /a TRIES=0
:warten
%ADB% -s %T% shell echo bereit 2>nul | find "bereit" >nul && goto verbunden
set /a TRIES+=1
if %TRIES% GEQ 20 goto koppeln
timeout /t 2 /nobreak >nul
%ADB% connect %T% >nul 2>&1
goto warten

:verbunden
echo   Verbunden.
echo [4/5] Installiere HA Shield TV Launcher ...
%ADB% -s %T% install -r tv-home.apk || goto fehler
%ADB% -s %T% shell appops set de.ingo.tvhome SYSTEM_ALERT_WINDOW allow
%ADB% -s %T% shell appops set de.ingo.tvhome REQUEST_INSTALL_PACKAGES allow >nul 2>&1
%ADB% -s %T% shell am start -n de.ingo.tvhome/.MainActivity >nul 2>&1

echo [5/5] Startseite festlegen
echo.
choice /c JN /m "  Soll HA Shield TV Launcher ab sofort die Startseite sein (Home-Taste)"
if errorlevel 2 goto ohnehome
%ADB% -s %T% shell pm disable-user --user 0 com.google.android.tvlauncher >nul 2>&1
%ADB% -s %T% shell pm disable-user --user 0 com.google.android.leanbacklauncher >nul 2>&1
rem Google TV (Chromecast mit Google TV / Google TV Streamer)
%ADB% -s %T% shell pm disable-user --user 0 com.google.android.apps.tv.launcherx >nul 2>&1
%ADB% -s %T% shell pm disable-user --user 0 com.google.android.tungsten.setupwraith >nul 2>&1
%ADB% -s %T% shell input keyevent KEYCODE_HOME
echo   HA Shield TV Launcher ist jetzt die Startseite.
goto fertig

:ohnehome
echo   Die Original-Startseite bleibt. HA Shield TV Launcher findest du bei deinen Apps.

:fertig
echo.
echo  Fertig! Auf dem Fernseher: Zahnrad (Einstellungen) - "Am Fernseher anmelden"
echo  oder "Mit dem Handy anmelden (QR-Code)".
echo.
pause
exit /b 0

:koppeln
echo.
echo  Keine Verbindung ueber Netzwerk-Debugging. Neuere Google-TV-Geraete brauchen
echo  stattdessen "Kabelloses Debugging" mit Kopplungscode:
echo   Einstellungen - System - Entwickleroptionen - Kabelloses Debugging - einschalten,
echo   dann "Geraet mit Kopplungscode koppeln" waehlen.
echo  (Nur Enter druecken, um abzubrechen.)
echo.
set PAIR=
set /p PAIR="  Angezeigte Kopplungs-Adresse (IP:Port): "
if "%PAIR%"=="" goto keineverbindung
set /p CODE="  Angezeigter Kopplungscode: "
%ADB% pair %PAIR% %CODE% || goto keineverbindung
echo.
echo  Gekoppelt. Jetzt die Adresse von der Seite "Kabelloses Debugging" (IP-Adresse und Port,
echo  NICHT die Kopplungs-Adresse) eingeben:
set /p T="  IP:Port: "
%ADB% connect %T% >nul 2>&1
%ADB% -s %T% shell echo bereit 2>nul | find "bereit" >nul && goto verbunden

:keineverbindung
echo.
echo  Das Geraet antwortet nicht. Bitte pruefen:
echo   - Ist das Geraet an und im gleichen Netz wie dieser Laptop?
echo   - Entwickleroptionen: "Netzwerk-Debugging" bzw. "USB-Debugging" eingeschaltet?
echo   - Wurde die Frage am Fernseher mit OK bestaetigt?
echo  Danach dieses Programm einfach nochmal starten.
echo.
pause
exit /b 1

:fehler
echo.
echo  Da ist etwas schiefgegangen (siehe Meldung oben). Bitte nochmal starten
echo  oder die Meldung an Claude schicken.
echo.
pause
exit /b 1
