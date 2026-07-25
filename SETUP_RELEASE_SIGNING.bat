@echo off
REM Release Keystore Setup Script for Fulguris
REM This script guides you through creating a release keystore

echo ============================================================
echo       Fulguris Release Keystore Setup
echo ============================================================
echo.
echo WARNING: You are about to create a release signing keystore.
echo.
echo IMPORTANT:
echo  - Choose a STRONG password (20+ characters recommended)
echo  - Write down ALL passwords in a password manager IMMEDIATELY
echo  - NEVER lose the keystore or passwords (cannot recover!)
echo  - Backup the keystore to multiple secure locations
echo.
echo ============================================================
echo.

pause

echo.
echo Step 1: Generating release keystore...
echo ============================================================
echo.
echo You will be prompted for:
echo   1. Keystore password (choose strong password)
echo   2. Key password (can be same as keystore password)
echo   3. Your name/organization info
echo.
echo IMPORTANT: Write down your passwords NOW before continuing!
echo.

pause

echo.
echo Running keytool...
echo.

keytool -genkey -v -keystore release.keystore -alias release-key -keyalg RSA -keysize 2048 -validity 10000

if errorlevel 1 (
    echo.
    echo ERROR: Keystore generation failed!
    echo.
    echo Possible causes:
    echo  - Java JDK not installed or not in PATH
    echo  - Incorrect input during prompts
    echo.
    echo Please install Java JDK and ensure keytool is accessible.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo SUCCESS: Keystore created successfully!
echo ============================================================
echo.
echo File created: release.keystore
echo.

pause

echo.
echo Step 2: Creating keystore.properties file...
echo ============================================================
echo.

if exist keystore.properties (
    echo WARNING: keystore.properties already exists!
    echo Do you want to overwrite it? Press Ctrl+C to cancel.
    pause
)

echo Creating keystore.properties from template...
copy keystore.properties.template keystore.properties

if errorlevel 1 (
    echo ERROR: Could not create keystore.properties
    pause
    exit /b 1
)

echo.
echo ============================================================
echo NEXT STEPS - IMPORTANT!
echo ============================================================
echo.
echo 1. EDIT keystore.properties file NOW:
echo    - Open: keystore.properties
echo    - Replace YOUR_KEYSTORE_PASSWORD with your actual password
echo    - Replace YOUR_KEY_PASSWORD with your actual password
echo    - Save the file
echo.
echo 2. BACKUP your keystore:
echo    - Copy release.keystore to a secure location
echo    - Store passwords in password manager
echo    - Create multiple backups
echo.
echo 3. VERIFY .gitignore protection:
echo    - Run: git status
echo    - Ensure keystore files are NOT listed
echo.
echo 4. BUILD and verify:
echo    - Run: gradlew.bat assembleSlionsFullDownloadRelease
echo    - Check for "BUILD SUCCESSFUL"
echo    - No warning about "No release signing config"
echo.
echo 5. VERIFY signature:
echo    - Use apksigner or keytool to verify APK is signed
echo.
echo ============================================================
echo.
echo Press any key to open keystore.properties for editing...
pause > nul

notepad keystore.properties

echo.
echo ============================================================
echo Setup script complete!
echo.
echo REMEMBER:
echo  - Backup keystore.keystore to multiple locations
echo  - Store passwords securely
echo  - Never commit keystore or keystore.properties to Git
echo.
echo See RELEASE_SIGNING_REQUIRED.md for detailed instructions.
echo ============================================================
echo.

pause
