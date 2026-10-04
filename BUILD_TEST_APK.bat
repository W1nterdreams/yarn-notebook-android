@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  pause
  exit /b 1
)
echo.
echo APK READY:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
pause
