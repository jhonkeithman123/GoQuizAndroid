@echo off
title GoQuiz - Android APK Builder
echo ========================================================
echo   GoQuiz Adventure - Android APK Builder
echo ========================================================
echo.
set "ROOT_DIR=%~dp0.."
cd /d "%ROOT_DIR%"

set "MODE=%~1"
if /i "%MODE%"=="release" goto do_release
if /i "%MODE%"=="debug" goto do_debug

:do_all
echo Building BOTH Debug and Release APKs... Please wait...
echo.
call gradlew.bat apk
if %ERRORLEVEL% neq 0 goto err
echo.
echo ========================================================
echo  APK generation finished!
echo  Saved to:
echo    - dist\app-debug.apk
echo    - dist\app-release.apk
echo ========================================================
goto finish

:do_release
set "VER_NAME=1.2.0"
for /f "tokens=2 delims=:, " %%a in ('findstr /i "versionName" app\assets\version.json 2^>nul') do (
    set "RAW_VER=%%~a"
    set "RAW_VER=!RAW_VER:"=!"
    set "RAW_VER=!RAW_VER: =!"
    if not "!RAW_VER!"=="" set "VER_NAME=!RAW_VER!"
)
echo Building Release APK (Optimized ^& Signed)... Please wait...
echo.
call gradlew.bat apkRelease
if %ERRORLEVEL% neq 0 goto err
echo.
echo ========================================================
echo  Release APK generation finished!
echo  Saved to:
echo    - dist\GoQuiz-v!VER_NAME!.apk
echo    - dist\app-release.apk
echo ========================================================
goto finish

:do_debug
echo Building Debug APK... Please wait...
echo.
call gradlew.bat apkDebug
if %ERRORLEVEL% neq 0 goto err
echo.
echo ========================================================
echo  Debug APK generation finished!
echo  Saved to: dist\app-debug.apk
echo ========================================================
goto finish

:err
echo.
echo [ERROR] Build failed! Check the output above.
pause
exit /b %ERRORLEVEL%

:finish
echo.
echo Opening dist folder...
if exist "dist" (
    explorer.exe "dist"
)
pause
