@echo off
title Quiz Adventure - Standalone Leaderboard Server
echo ===================================================
echo   Quiz Adventure Standalone Leaderboard Server
echo ===================================================
echo.
echo Compiling and starting QuizServer on port 5050...
echo Ensure devices/phones are connected to the same Wi-Fi/LAN.
echo.
javac -encoding UTF-8 QuizServer.java
if %ERRORLEVEL% neq 0 (
    echo Error compiling QuizServer.java
    pause
    exit /b %ERRORLEVEL%
)
java QuizServer 5050
pause
