@echo off
title Quiz Adventure - Standalone Leaderboard Server
echo ===================================================
echo   Quiz Adventure Standalone Leaderboard Server
echo ===================================================
echo.
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%"
echo Compiling QuizServer...
javac -encoding UTF-8 -d server app\src\goquiz\QuizServer.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)
echo Starting QuizServer on port 5050...
echo Ensure devices/phones are connected to the same Wi-Fi/LAN.
java -cp server goquiz.QuizServer 5050
pause
