@echo off
title GoQuiz - Clean Project
echo ========================================================
echo   GoQuiz Adventure - Clean Build Caches ^& Temp Files
echo ========================================================
echo.
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%"
echo Cleaning Gradle build caches...
call gradlew.bat clean
echo.
echo Cleaning desktop and server compiled classes...
del /q /f desktop\*.class >nul 2>&1
del /q /f server\*.class >nul 2>&1
echo.
echo ========================================================
echo  [SUCCESS] Project cleaned successfully!
echo ========================================================
pause
