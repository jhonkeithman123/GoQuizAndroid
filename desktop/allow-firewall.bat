@echo off
title GoQuiz - Windows Firewall Configuration
echo ========================================================
echo    GoQuiz - Allowing Inbound Network Connections
echo ========================================================
echo.
echo Checking for Administrator permissions...
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Elevating permissions (Windows UAC prompt will appear)...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\" -elevated' -Verb RunAs"
    exit /b
)

echo.
echo Adding Windows Defender Firewall rules for GoQuiz...
echo Allowing TCP port 5050 (Leaderboard Server)...
netsh advfirewall firewall delete rule name="GoQuiz Leaderboard TCP (Port 5050)" >nul 2>&1
netsh advfirewall firewall add rule name="GoQuiz Leaderboard TCP (Port 5050)" dir=in action=allow protocol=TCP localport=5050 profile=any

echo Allowing UDP port 5052 (Auto-Discovery Probe)...
netsh advfirewall firewall delete rule name="GoQuiz Leaderboard UDP (Port 5052)" >nul 2>&1
netsh advfirewall firewall add rule name="GoQuiz Leaderboard UDP (Port 5052)" dir=in action=allow protocol=UDP localport=5052 profile=any

echo.
echo ========================================================
echo SUCCESS: Windows Firewall rules configured!
echo Phones and other PCs can now connect to this computer.
echo ========================================================
echo.
pause
