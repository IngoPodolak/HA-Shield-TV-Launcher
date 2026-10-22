@echo off
chcp 65001 >nul
title TV Home installieren
setlocal

rem ---- Adresse des Shield TV im Heimnetz
set SHIELD=192.168.0.158
set DIR=%LOCALAPPDATA%\TVHome
set ADB="%LOCALAPPDATA%\TVHome\platform-tools\adb.exe"

echo.
echo  ============================================
echo    TV Home fuer den NVIDIA Shield installieren
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
curl -sfL -o tv-home.apk https://github.com/IngoPodolak/Nvidia-Shield-Launcher/releases/latest/download/tv-home.apk || goto fehler

echo [3/5] Verbinde mit dem Shield (%SHIELD%) ...
echo.
echo   WICHTIG: Auf dem Fernseher erscheint jetzt eventuell die Frage
echo   "USB-Debugging zulassen?". Dort "Immer von diesem Computer zulassen"
echo   ankreuzen und OK druecken.
echo.
%ADB% disconnect >nul 2>&1
%ADB% connect %SHIELD%:5555 >nul 2>&1
set /a TRIES=0
:warten
%ADB% -s %SHIELD%:5555 shell echo bereit 2>nul | find "bereit" >nul && goto verbunden
set /a TRIES+=1
if %TRIES% GEQ 45 goto keineverbindung
timeout /t 2 /nobreak >nul
%ADB% connect %SHIELD%:5555 >nul 2>&1
goto warten

:verbunden
echo   Verbunden.
echo [4/5] Installiere TV Home ...
%ADB% -s %SHIELD%:5555 install -r tv-home.apk || goto fehler
%ADB% -s %SHIELD%:5555 shell appops set de.ingo.tvhome SYSTEM_ALERT_WINDOW allow
%ADB% -s %SHIELD%:5555 shell appops set de.ingo.tvhome REQUEST_INSTALL_PACKAGES allow >nul 2>&1
%ADB% -s %SHIELD%:5555 shell am start -n de.ingo.tvhome/.MainActivity >nul 2>&1

echo [5/5] Startseite festlegen
echo.
choice /c JN /m "  Soll TV Home ab sofort die Startseite sein (Home-Taste)"
if errorlevel 2 goto ohnehome
%ADB% -s %SHIELD%:5555 shell pm disable-user --user 0 com.google.android.tvlauncher >nul 2>&1
%ADB% -s %SHIELD%:5555 shell pm disable-user --user 0 com.google.android.leanbacklauncher >nul 2>&1
%ADB% -s %SHIELD%:5555 shell input keyevent KEYCODE_HOME
echo   TV Home ist jetzt die Startseite.
goto fertig

:ohnehome
echo   Die Original-Startseite bleibt. TV Home findest du bei deinen Apps.

:fertig
echo.
echo  Fertig! Auf dem Fernseher: Zahnrad (Einstellungen) - "Am Fernseher anmelden"
echo  oder "Mit dem Handy anmelden (QR-Code)".
echo.
pause
exit /b 0

:keineverbindung
echo.
echo  Der Shield antwortet nicht. Bitte pruefen:
echo   - Ist der Shield an und im gleichen Netz wie dieser Laptop?
echo   - Entwickleroptionen: "Netzwerk-Debugging" eingeschaltet?
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
