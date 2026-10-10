@echo off
setlocal enabledelayedexpansion
title GoQuiz Adventure - Command Center

cd /d "%~dp0"

set "CMD=%~1"
set "SUB=%~2"

if "%CMD%"=="" goto menu
if /i "%CMD%"=="help" goto help
if /i "%CMD%"=="-h" goto help
if /i "%CMD%"=="--help" goto help
if /i "%CMD%"=="?" goto help

if /i "%CMD%"=="apk" goto do_apk
if /i "%CMD%"=="build" goto do_apk
if /i "%CMD%"=="build-apk" goto do_apk

if /i "%CMD%"=="dist" goto do_dist
if /i "%CMD%"=="package" goto do_dist
if /i "%CMD%"=="exe" goto do_exe
if /i "%CMD%"=="build-exe" goto do_exe

if /i "%CMD%"=="release" goto do_release
if /i "%CMD%"=="debug" goto do_debug

if /i "%CMD%"=="install" goto do_install
if /i "%CMD%"=="install-app" goto do_install
if /i "%CMD%"=="run-app" goto do_install

if /i "%CMD%"=="desktop" goto do_desktop
if /i "%CMD%"=="game" goto do_desktop
if /i "%CMD%"=="run-desktop" goto do_desktop

if /i "%CMD%"=="server" goto do_server
if /i "%CMD%"=="run-server" goto do_server

if /i "%CMD%"=="firewall" goto do_firewall
if /i "%CMD%"=="allow-firewall" goto do_firewall

if /i "%CMD%"=="clean" goto do_clean

echo [ERROR] Unknown command: "%CMD%"
echo.
goto help

:menu
cls
echo ========================================================
echo        GoQuiz Adventure - Command Center (CLI)
echo ========================================================
echo.
echo  Available Commands:
echo.
echo    [1] apk       - Build BOTH Debug ^& Release APKs
echo    [2] release   - Build Release APK (GoQuiz-v{ver}.apk)
echo    [3] debug     - Build Debug APK only (fast build)
echo    [4] exe       - Build Desktop Windows Executable (GoQuiz-v{ver}.exe)
echo    [5] dist      - Build FULL Release Bundle (.apk ^& .exe)
echo    [6] install   - Install APK to phone / emulator and launch
echo    [7] desktop   - Compile and launch Desktop Java Game
echo    [8] server    - Run Standalone Quiz Server (port 5050)
echo    [9] firewall  - Allow Ports 5050 ^& 5052 through Windows Firewall
echo    [10] clean    - Clean Gradle cache ^& compiled classes
echo    [0] exit      - Exit
echo.
echo ========================================================
echo  Tip: You can run directly from command line anytime:
echo       goquiz dist       or   goquiz release
echo       goquiz exe        or   goquiz desktop
echo ========================================================
echo.
set /p "CHOICE=Enter choice [1-10 or command name]: "

if "%CHOICE%"=="1" goto do_apk
if "%CHOICE%"=="2" goto do_release
if "%CHOICE%"=="3" goto do_debug
if "%CHOICE%"=="4" goto do_exe
if "%CHOICE%"=="5" goto do_dist
if "%CHOICE%"=="6" goto do_install
if "%CHOICE%"=="7" goto do_desktop
if "%CHOICE%"=="8" goto do_server
if "%CHOICE%"=="9" goto do_firewall
if "%CHOICE%"=="10" goto do_clean
if "%CHOICE%"=="0" exit /b 0

if /i "%CHOICE%"=="apk" goto do_apk
if /i "%CHOICE%"=="build" goto do_apk
if /i "%CHOICE%"=="release" goto do_release
if /i "%CHOICE%"=="debug" goto do_debug
if /i "%CHOICE%"=="exe" goto do_exe
if /i "%CHOICE%"=="dist" goto do_dist
if /i "%CHOICE%"=="package" goto do_dist
if /i "%CHOICE%"=="install" goto do_install
if /i "%CHOICE%"=="desktop" goto do_desktop
if /i "%CHOICE%"=="server" goto do_server
if /i "%CHOICE%"=="firewall" goto do_firewall
if /i "%CHOICE%"=="clean" goto do_clean
if /i "%CHOICE%"=="exit" exit /b 0

echo Invalid choice.
pause
goto menu

:do_dist
call "%~dp0scripts\build-dist.bat"
goto end

:do_exe
call "%~dp0scripts\build-desktop-exe.bat"
goto end

:do_apk
if /i "%SUB%"=="release" goto do_release
if /i "%SUB%"=="debug" goto do_debug
call "%~dp0scripts\build-apk.bat" all
goto end

:do_release
call "%~dp0scripts\build-apk.bat" release
goto end

:do_debug
call "%~dp0scripts\build-apk.bat" debug
goto end

:do_install
call "%~dp0scripts\install-app.bat"
goto end

:do_desktop
call "%~dp0scripts\run-desktop.bat"
goto end

:do_server
call "%~dp0scripts\run-server.bat"
goto end

:do_firewall
call "%~dp0scripts\allow-firewall.bat"
goto end

:do_clean
call "%~dp0scripts\clean.bat"
goto end

:help
echo ========================================================
echo        GoQuiz Adventure - Command Center (CLI)
echo ========================================================
echo.
echo  Usage: goquiz ^<command^>
echo.
echo  Commands:
echo    goquiz dist      - Build FULL Release Bundle (GoQuiz-v{ver}.apk ^& .exe)
echo    goquiz exe       - Build Desktop Windows Executable (GoQuiz-v{ver}.exe)
echo    goquiz release   - Build Release APK only (GoQuiz-v{ver}.apk)
echo    goquiz apk       - Build BOTH Debug and Release APKs
echo    goquiz debug     - Build Debug APK only (fast build)
echo    goquiz install   - Install and run APK on phone / emulator
echo    goquiz desktop   - Compile and play Desktop Java version
echo    goquiz server    - Start QuizServer for multiplayer
echo    goquiz firewall  - Configure Windows Firewall rules
echo    goquiz clean     - Clean build caches and compiled classes
echo    goquiz help      - Show this help message
echo.
echo  If you type just "goquiz" without arguments, an interactive
echo  menu will open.
echo ========================================================
exit /b 0

:end
exit /b %ERRORLEVEL%
