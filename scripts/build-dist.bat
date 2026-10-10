@echo off
setlocal enabledelayedexpansion
title GoQuiz Adventure - Release Package Builder
echo ========================================================
echo   GoQuiz Adventure - Multi-Platform Distribution Builder
echo ========================================================
echo.

cd /d "%~dp0\.."

set "VER_NAME=1.2.0"
for /f "tokens=2 delims=:, " %%a in ('findstr /i "versionName" app\assets\version.json 2^>nul') do (
    set "RAW_VER=%%~a"
    set "RAW_VER=!RAW_VER:"=!"
    set "RAW_VER=!RAW_VER: =!"
    if not "!RAW_VER!"=="" set "VER_NAME=!RAW_VER!"
)

echo Target Release Version: v!VER_NAME!
echo.

if not exist "dist" mkdir "dist"

echo [STEP 1/2] Building Android Release APK (GoQuiz-v!VER_NAME!.apk)...
call gradlew.bat apkRelease
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Android Release build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [STEP 2/2] Building Desktop Windows Executable (GoQuiz-v!VER_NAME!.exe)...
call "%~dp0build-desktop-exe.bat"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Desktop build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo   [SUCCESS] DISTRIBUTION PACKAGES READY (v!VER_NAME!)
echo ========================================================
echo.
echo  Mobile Android:
echo    - dist\GoQuiz-v!VER_NAME!.apk
echo.
echo  Desktop Windows:
echo    - dist\GoQuiz-v!VER_NAME!.exe (Self-contained launcher)
echo    - dist\GoQuiz-v!VER_NAME!-Windows.zip (Compressed bundle)
echo    - dist\GoQuiz-v!VER_NAME!.jar
echo.
echo ========================================================
explorer "%~dp0..\dist"
pause
