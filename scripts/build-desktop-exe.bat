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

set "VER_NAME=1.2.0"
for /f "tokens=2 delims=:, " %%a in ('findstr /i "versionName" app\assets\version.json 2^>nul') do (
    set "RAW_VER=%%~a"
    set "RAW_VER=!RAW_VER:"=!"
    set "RAW_VER=!RAW_VER: =!"
    if not "!RAW_VER!"=="" set "VER_NAME=!RAW_VER!"
)

echo [2/4] Packaging GoQuiz-v!VER_NAME!.jar...
if not exist "dist" mkdir "dist"
jar cfe "dist/GoQuiz-v!VER_NAME!.jar" QuizGame -C desktop .
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
    %CSC% /nologo /target:winexe /win32icon:app\assets\images\GoQuiz.ico /resource:"dist\GoQuiz-v!VER_NAME!.jar",GoQuiz.jar /out:"dist\GoQuiz-v!VER_NAME!.exe" scripts\launcher.cs
    if %errorlevel% neq 0 (
        echo [WARN] Compilation with icon failed, compiling without icon...
        %CSC% /nologo /target:winexe /resource:"dist\GoQuiz-v!VER_NAME!.jar",GoQuiz.jar /out:"dist\GoQuiz-v!VER_NAME!.exe" scripts\launcher.cs
    )
    echo [SUCCESS] Built bundled standalone dist\GoQuiz-v!VER_NAME!.exe successfully!
) else (
    echo [WARN] csc.exe not found. Standalone JAR created: dist\GoQuiz-v!VER_NAME!.jar
)

del /q "dist\GoQuiz.exe" "dist\GoQuizAdventure.jar" 2>nul

echo [4/4] Compressing desktop release bundle...
powershell -NoProfile -Command "Compress-Archive -Path 'dist\GoQuiz-v!VER_NAME!.exe', 'dist\GoQuiz-v!VER_NAME!.jar' -DestinationPath 'dist\GoQuiz-v!VER_NAME!-Windows.zip' -Force"

echo ===================================================
echo [SUCCESS] Desktop distribution ready in dist\:
echo   - dist\GoQuiz-v!VER_NAME!.exe       (Standalone launcher with embedded game JAR)
echo   - dist\GoQuiz-v!VER_NAME!-Windows.zip (Compressed bundle)
echo   - dist\GoQuiz-v!VER_NAME!.jar       (Cross-platform runnable package)
echo ===================================================
