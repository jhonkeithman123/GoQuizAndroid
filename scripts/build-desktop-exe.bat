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

echo [4/5] Generating portable zero-dependency Java 17 runtime...
set "HAS_JRE=0"
where jlink >nul 2>nul
if %errorlevel% equ 0 (
    if exist "dist\jre" rmdir /s /q "dist\jre"
    jlink --add-modules java.base,java.desktop --strip-debug --no-man-pages --no-header-files --compress=2 --output "dist\jre"
    if exist "dist\jre\bin\javaw.exe" set "HAS_JRE=1"
)

echo [5/5] Compressing complete desktop release bundle...
if exist "dist\app" rmdir /s /q "dist\app"
mkdir "dist\app\assets" 2>nul
xcopy /e /i /y "app\assets" "dist\app\assets" >nul

if "!HAS_JRE!"=="1" (
    powershell -NoProfile -Command "Compress-Archive -Path 'dist\GoQuiz-v!VER_NAME!.exe', 'dist\app', 'dist\jre' -DestinationPath 'dist\GoQuiz-v!VER_NAME!-Windows.zip' -Force"
    rmdir /s /q "dist\jre"
    rmdir /s /q "dist\app"
    echo [SUCCESS] Bundled portable Java 17 and assets into dist\GoQuiz-v!VER_NAME!-Windows.zip - zero install required
) else (
    powershell -NoProfile -Command "Compress-Archive -Path 'dist\GoQuiz-v!VER_NAME!.exe', 'dist\app' -DestinationPath 'dist\GoQuiz-v!VER_NAME!-Windows.zip' -Force"
    rmdir /s /q "dist\app"
)

echo ===================================================
echo [SUCCESS] Desktop distribution ready in dist\:
echo   - dist\GoQuiz-v!VER_NAME!-Windows.zip (Zero-setup bundle - runs on any PC with no Java needed)
echo   - dist\GoQuiz-v!VER_NAME!.exe         (Standalone launcher with embedded game JAR)
echo   - dist\GoQuiz-v!VER_NAME!.jar         (Cross-platform runnable package for Mac/Linux)
echo ===================================================
