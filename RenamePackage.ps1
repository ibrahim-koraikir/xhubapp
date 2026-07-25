# Package Rename Script: fulguris → com.xhub.browser
# This script performs a comprehensive package rename across the entire codebase

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Package Rename: fulguris → com.xhub.browser" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Update all package declarations in .kt and .java files
Write-Host "[1/6] Updating package declarations..." -ForegroundColor Yellow

$kotlinJavaFiles = Get-ChildItem -Path "app\src" -Recurse -Include "*.kt","*.java"
$packageCount = 0

foreach ($file in $kotlinJavaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Replace package declarations
    # Match: package fulguris
    # Match: package fulguris.activity
    # Match: package fulguris.browser.tabs
    $content = $content -replace '(?m)^package fulguris(\.[a-zA-Z0-9_.]+)?', 'package com.xhub.browser$1'
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        $packageCount++
    }
}

Write-Host "  ✓ Updated $packageCount files with package declarations" -ForegroundColor Green

# Step 2: Update all import statements in .kt and .java files
Write-Host "[2/6] Updating import statements..." -ForegroundColor Yellow

$importCount = 0
foreach ($file in $kotlinJavaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Replace import statements
    # Match: import fulguris.activity.MainActivity
    # Match: import fulguris.browser.*
    $content = $content -replace '(?m)^import fulguris(\.[a-zA-Z0-9_.]+)?(\.\*)?', 'import com.xhub.browser$1$2'
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        $importCount++
    }
}

Write-Host "  ✓ Updated $importCount files with import statements" -ForegroundColor Green

# Step 3: Update fully-qualified class references in code
Write-Host "[3/6] Updating fully-qualified class references..." -ForegroundColor Yellow

$fqnCount = 0
foreach ($file in $kotlinJavaFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Replace fully-qualified class names in code (not in strings)
    # Match: fulguris.Sponsorship.BRONZE
    # Match: fulguris.activity.MainActivity::class
    # But be careful not to match inside strings
    $content = $content -replace '\bfulguris\.([A-Z][a-zA-Z0-9_]*)', 'com.xhub.browser.$1'
    
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        $fqnCount++
    }
}

Write-Host "  ✓ Updated $fqnCount files with fully-qualified references" -ForegroundColor Green

# Step 4: Move directory structure
Write-Host "[4/6] Moving directory structure..." -ForegroundColor Yellow

$sourceDirs = @(
    "app\src\main\java\fulguris",
    "app\src\test\java\fulguris",
    "app\src\androidTest\java\fulguris",
    "app\src\download\java\fulguris",
    "app\src\fdroid\java\fulguris",
    "app\src\playstore\java\fulguris"
)

$moved = 0
foreach ($sourceDir in $sourceDirs) {
    if (Test-Path $sourceDir) {
        Write-Host "  Moving $sourceDir..." -ForegroundColor Gray
        
        # Get the parent directory (e.g., app\src\main\java)
        $parent = Split-Path $sourceDir -Parent
        
        # Create new directory structure: com\xhub\browser
        $targetDir = Join-Path $parent "com\xhub\browser"
        $comDir = Join-Path $parent "com"
        $xhubDir = Join-Path $parent "com\xhub"
        
        # Create directories if they don't exist
        if (-not (Test-Path $comDir)) {
            New-Item -ItemType Directory -Path $comDir -Force | Out-Null
        }
        if (-not (Test-Path $xhubDir)) {
            New-Item -ItemType Directory -Path $xhubDir -Force | Out-Null
        }
        
        # Move the fulguris directory contents to com\xhub\browser
        Move-Item -Path $sourceDir -Destination $targetDir -Force
        $moved++
    }
}

Write-Host "  ✓ Moved $moved directory trees" -ForegroundColor Green

# Step 5: Update build.gradle
Write-Host "[5/6] Updating build.gradle..." -ForegroundColor Yellow

$buildGradle = "app\build.gradle"
$content = Get-Content $buildGradle -Raw -Encoding UTF8

# Line 40: generatedLocaleListDir
$content = $content -replace "generated/source/locale/fulguris/locale", "generated/source/locale/com/xhub/browser/locale"

# Line 63: namespace
$content = $content -replace "namespace = 'fulguris'", "namespace = 'com.xhub.browser'"

# Lines 194, 200, 206, 212: buildConfigField Sponsorship
$content = $content -replace '"fulguris\.Sponsorship"', '"com.xhub.browser.Sponsorship"'
$content = $content -replace '"fulguris\.Sponsorship\.', '"com.xhub.browser.Sponsorship.'

# Line 472: package fulguris.locale
$content = $content -replace 'package fulguris\.locale;', 'package com.xhub.browser.locale;'

Set-Content -Path $buildGradle -Value $content -Encoding UTF8 -NoNewline
Write-Host "  ✓ Updated build.gradle" -ForegroundColor Green

# Step 6: Update AndroidManifest.xml
Write-Host "[6/6] Updating AndroidManifest.xml..." -ForegroundColor Yellow

$manifest = "app\src\main\AndroidManifest.xml"
$content = Get-Content $manifest -Raw -Encoding UTF8

# Replace all android:name, android:targetActivity, android:parentActivityName
# Pattern: fulguris. → com.xhub.browser.
$content = $content -replace 'fulguris\.', 'com.xhub.browser.'

Set-Content -Path $manifest -Value $content -Encoding UTF8 -NoNewline
Write-Host "  ✓ Updated AndroidManifest.xml" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Package Rename Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  • Package declarations: $packageCount files" -ForegroundColor White
Write-Host "  • Import statements: $importCount files" -ForegroundColor White
Write-Host "  • Fully-qualified refs: $fqnCount files" -ForegroundColor White
Write-Host "  • Directory moves: $moved trees" -ForegroundColor White
Write-Host "  • build.gradle: updated" -ForegroundColor White
Write-Host "  • AndroidManifest.xml: updated" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Run: .\gradlew.bat clean" -ForegroundColor White
Write-Host "  2. Run: .\gradlew.bat assembleXhubFullDownloadDebug" -ForegroundColor White
Write-Host ""
