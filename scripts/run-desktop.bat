@echo off
title Quiz Adventure - Desktop Edition
echo ===================================================
echo   Quiz Adventure - Desktop Game Edition
echo ===================================================
echo.
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%"
echo Compiling QuizGame and QuizServer...
javac -encoding UTF-8 -d desktop app\src\goquiz\QuizServer.java desktop\QuizGame.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)
echo Launching QuizGame...
java -cp desktop QuizGame
pause
