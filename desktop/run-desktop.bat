@echo off
title Quiz Adventure - Desktop Edition
echo ===================================================
echo   Quiz Adventure - Desktop Game Edition
echo ===================================================
echo.
cd /d "%~dp0\.."
echo Compiling QuizGame and QuizServer...
javac -encoding UTF-8 desktop\QuizServer.java desktop\QuizGame.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)
echo Launching QuizGame...
java -cp desktop QuizGame
pause
