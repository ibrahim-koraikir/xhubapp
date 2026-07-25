# Firebase Configuration Setup

## Security Notice

⚠️ **IMPORTANT**: The `app/google-services.json` file in this repository is a placeholder and does NOT contain real API keys.

The actual Firebase configuration file with live API keys should **NEVER** be committed to version control as it contains sensitive credentials.

## Setup Instructions

### Option 1: Use Firebase (Recommended for production builds)

1. **Create a Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com)
   - Create a new project or use an existing one
   - Note your project ID

2. **Register Your Android App**
   - In Firebase Console, add an Android app
   - Use the correct package name for your build flavor:
     - Download: `net.slions.fulguris.full.download`
     - Play Store: `net.slions.fulguris.full.playstore`
     - F-Droid: `net.slions.fulguris.full.fdroid`

3. **Download Configuration File**
   - Download the `google-services.json` file from Firebase Console
   - Replace `app/google-services.json` with your downloaded file
   - **DO NOT commit this file to git** (it's already in .gitignore)

4. **Verify Setup**
   - Build the app: `./gradlew assembleSlionsFullDownloadDebug`
   - Check Firebase Console to confirm the app is connected

### Option 2: Build Without Firebase (For development/testing)

If you don't need Firebase features (Analytics, Crashlytics), you can disable it:

1. **Remove Firebase Configuration**
   - Delete or keep the placeholder `app/google-services.json` file

2. **Modify `app/build.gradle`**
   - Remove or comment out:
     ```gradle
     apply plugin: 'com.google.gms.google-services'
     apply plugin: 'com.google.firebase.crashlytics'
     ```
   - Remove Firebase dependencies:
     ```gradle
     implementation platform('com.google.firebase:firebase-bom:...')
     implementation 'com.google.firebase:firebase-analytics-ktx'
     implementation 'com.google.firebase:firebase-crashlytics-ktx'
     ```

3. **Update Build Flavor Configuration**
   - Check source sets in `app/build.gradle` that reference Firebase
   - Ensure flavor-specific Firebase stubs are in place

## Current Firebase API Key Status

✅ **SECURED**: The committed `google-services.json` file is now a placeholder template

The previous live API key has been removed from the repository to prevent:
- Unauthorized usage of Firebase quota
- Security vulnerabilities
- Accidental exposure of credentials

## For Maintainers

When setting up CI/CD:
- Store `google-services.json` as a secret/encrypted file
- Inject it during build time
- Never include it in build artifacts or logs

## Build Flavors

The app supports multiple product flavors:
- **slionsFullDownload**: Download distribution (unlimited tabs)
- **slionsFullPlaystore**: Google Play distribution (requires subscription for >20 tabs)
- **slionsFullFdroid**: F-Droid distribution

Each flavor should have its own Firebase project registration if using Firebase.
