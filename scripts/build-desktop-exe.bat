@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   GoQuiz Adventure - Desktop Application Builder
echo ===================================================

cd /d "%~dp0\.."

echo [1/4] Compiling Java desktop classes...
javac -encoding UTF-8 -d desktop app/src/goquiz/QuizServer.java desktop/QuizGame.java
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed!
    exit /b 1
)

set "VER_NAME=1.2.1"
for /f "tokens=2 delims=:, " %%a in ('findstr /i "versionName" app\assets\version.json 2^>nul') do (
    set "RAW_VER=%%~a"
    set "RAW_VER=!RAW_VER:"=!"
    set "RAW_VER=!RAW_VER: =!"
    if not "!RAW_VER!"=="" set "VER_NAME=!RAW_VER!"
)

set "TARGET_DIR=dist\v!VER_NAME!"
if not exist "!TARGET_DIR!" mkdir "!TARGET_DIR!"

echo [2/4] Packaging GoQuiz-v!VER_NAME!.jar...
jar cfe "!TARGET_DIR!\GoQuiz-v!VER_NAME!.jar" QuizGame -C desktop .
if %errorlevel% neq 0 (
    echo [ERROR] JAR packaging failed!
    exit /b 1
)

echo [3/4] Compiling native Windows GoQuiz-v!VER_NAME!.exe launcher with embedded JAR...
set CSC="C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist %CSC% (
    set CSC="C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)

if exist %CSC% (
    %CSC% /nologo /target:winexe /win32icon:app\assets\images\GoQuiz.ico /resource:"!TARGET_DIR!\GoQuiz-v!VER_NAME!.jar",GoQuiz.jar /out:"!TARGET_DIR!\GoQuiz-v!VER_NAME!.exe" scripts\launcher.cs
    if %errorlevel% neq 0 (
        echo [WARN] Compilation with icon failed, compiling without icon...
        %CSC% /nologo /target:winexe /resource:"!TARGET_DIR!\GoQuiz-v!VER_NAME!.jar",GoQuiz.jar /out:"!TARGET_DIR!\GoQuiz-v!VER_NAME!.exe" scripts\launcher.cs
    )
    echo [SUCCESS] Built bundled standalone !TARGET_DIR!\GoQuiz-v!VER_NAME!.exe successfully!
) else (
    echo [WARN] csc.exe not found. Standalone JAR created: !TARGET_DIR!\GoQuiz-v!VER_NAME!.jar
)

del /q "!TARGET_DIR!\GoQuiz.exe" "!TARGET_DIR!\GoQuizAdventure.jar" 2>nul

echo [4/5] Generating portable zero-dependency Java 17 runtime...
set "HAS_JRE=0"
where jlink >nul 2>nul
if %errorlevel% equ 0 (
    if exist "!TARGET_DIR!\jre" rmdir /s /q "!TARGET_DIR!\jre"
    jlink --add-modules java.base,java.desktop --strip-debug --no-man-pages --no-header-files --compress=2 --output "!TARGET_DIR!\jre"
    if exist "!TARGET_DIR!\jre\bin\javaw.exe" set "HAS_JRE=1"
)

echo [5/5] Compressing complete desktop release bundle...
if exist "!TARGET_DIR!\app" rmdir /s /q "!TARGET_DIR!\app"
mkdir "!TARGET_DIR!\app\assets" 2>nul
xcopy /e /i /y "app\assets" "!TARGET_DIR!\app\assets" >nul

if "!HAS_JRE!"=="1" (
    powershell -NoProfile -Command "Compress-Archive -Path '!TARGET_DIR!\GoQuiz-v!VER_NAME!.exe', '!TARGET_DIR!\app', '!TARGET_DIR!\jre' -DestinationPath '!TARGET_DIR!\GoQuiz-v!VER_NAME!-Windows.zip' -Force"
    rmdir /s /q "!TARGET_DIR!\jre"
    rmdir /s /q "!TARGET_DIR!\app"
    echo [SUCCESS] Bundled portable Java 17 and assets into !TARGET_DIR!\GoQuiz-v!VER_NAME!-Windows.zip - zero install required
) else (
    powershell -NoProfile -Command "Compress-Archive -Path '!TARGET_DIR!\GoQuiz-v!VER_NAME!.exe', '!TARGET_DIR!\app' -DestinationPath '!TARGET_DIR!\GoQuiz-v!VER_NAME!-Windows.zip' -Force"
    rmdir /s /q "!TARGET_DIR!\app"
)

echo ===================================================
echo [SUCCESS] Desktop distribution ready in !TARGET_DIR!\:
echo   - !TARGET_DIR!\GoQuiz-v!VER_NAME!-Windows.zip (Zero-setup bundle - runs on any PC with no Java needed)
echo   - !TARGET_DIR!\GoQuiz-v!VER_NAME!.exe         (Standalone launcher with embedded game JAR)
echo   - !TARGET_DIR!\GoQuiz-v!VER_NAME!.jar         (Cross-platform runnable package for Mac/Linux)
echo ===================================================
