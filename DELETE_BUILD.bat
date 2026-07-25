@echo off
echo ========================================
echo Deleting Build Folders
echo ========================================
echo.

echo Stopping Gradle Daemons...
call gradlew --stop
timeout /t 2 /nobreak >nul

echo.
echo Deleting app\build folder...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo Done: app\build deleted
) else (
    echo app\build folder not found
)

echo.
echo Deleting .gradle folder...
if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo Done: .gradle deleted
) else (
    echo .gradle folder not found
)

echo.
echo Deleting other build folders...
if exist "build" (
    rmdir /s /q "build"
    echo Done: build deleted
)

if exist "subs\Preference\lib\build" (
    rmdir /s /q "subs\Preference\lib\build"
    echo Done: subs\Preference\lib\build deleted
)

echo.
echo ========================================
echo All build folders deleted!
echo ========================================
echo.
echo Now do this:
echo 1. Open Android Studio
echo 2. Let it index completely
echo 3. Build -^> Build Bundle(s) / APK(s) -^> Build APK(s)
echo 4. Wait for build to complete
echo 5. Uninstall old app from device
echo 6. Install new APK
echo.
pause
