@echo off
chcp 65001 >nul
title TV Home entfernen
set SHIELD=192.168.0.158
set ADB="%LOCALAPPDATA%\TVHome\platform-tools\adb.exe"
if not exist %ADB% (
  echo Die Android-Werkzeuge fehlen. Bitte zuerst "TV Home installieren.bat" einmal starten.
  pause
  exit /b 1
)
echo Stelle die Original-Startseite wieder her und entferne TV Home ...
%ADB% connect %SHIELD%:5555 >nul 2>&1
%ADB% -s %SHIELD%:5555 shell pm enable com.google.android.tvlauncher
%ADB% -s %SHIELD%:5555 shell pm enable com.google.android.leanbacklauncher >nul 2>&1
%ADB% -s %SHIELD%:5555 uninstall de.ingo.tvhome
%ADB% -s %SHIELD%:5555 shell input keyevent KEYCODE_HOME
echo Fertig. Der Shield hat wieder seine normale Startseite.
pause
