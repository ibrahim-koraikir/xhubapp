@echo off
echo ========================================
echo Fulguris Clean Build Script
echo ========================================
echo.

echo Step 1: Stopping all Gradle daemons...
call gradlew --stop
timeout /t 3 /nobreak >nul

echo.
echo Step 2: Killing any remaining Java processes...
taskkill /F /IM java.exe /T 2>nul
timeout /t 3 /nobreak >nul

echo.
echo Step 3: Cleaning build directories...
if exist "app\build" (
    echo Deleting app\build...
    rmdir /s /q "app\build" 2>nul
)
if exist ".gradle" (
    echo Deleting .gradle cache...
    rmdir /s /q ".gradle" 2>nul
)
if exist "Preference\build" (
    echo Deleting Preference\build...
    rmdir /s /q "Preference\build" 2>nul
)
timeout /t 2 /nobreak >nul

echo.
echo Step 4: Starting fresh build...
echo This will take 3-5 minutes. Please be patient...
echo.
call gradlew assembleSlionsFullDownloadDebug --no-daemon --stacktrace

echo.
echo ========================================
if %ERRORLEVEL% EQU 0 (
    echo BUILD SUCCESSFUL!
    echo.
    echo APK location:
    echo app\build\outputs\apk\slionsFullDownload\debug\app-slions-full-download-debug.apk
    echo.
    echo To install: adb install -r app\build\outputs\apk\slionsFullDownload\debug\app-slions-full-download-debug.apk
) else (
    echo BUILD FAILED!
    echo.
    echo If build keeps failing:
    echo 1. Close ALL programs (Android Studio, terminals, etc.)
    echo 2. RESTART your computer
    echo 3. Run this script again
)
echo ========================================
pause
