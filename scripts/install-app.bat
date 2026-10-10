@echo off
title GoQuiz - Install to Phone / Emulator
echo ========================================================
echo   GoQuiz Adventure - Install on Phone or Emulator
echo ========================================================
echo.
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%"
echo Checking for connected Android devices...
adb devices
echo.
echo Installing APK to device...
call gradlew.bat run
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Installation failed! Make sure your phone has USB Debugging enabled.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Launching GoQuiz on device...
adb shell am start -n com.goquiz.adventure/goquiz.MainActivity
echo.
echo ========================================================
echo  [SUCCESS] GoQuiz is now running on your device!
echo ========================================================
pause
