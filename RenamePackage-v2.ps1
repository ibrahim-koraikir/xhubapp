# Package Rename Script v2: fulguris → com.xhub.browser
# More robust version with better error handling

$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Package Rename: fulguris → com.xhub.browser" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Update all Kotlin/Java files - package declarations and imports
Write-Host "[1/5] Updating Kotlin and Java files..." -ForegroundColor Yellow

$files = Get-ChildItem -Path "app\src" -Recurse -Include "*.kt","*.java" -File
$totalFiles = $files.Count
$updatedFiles = 0
$current = 0

foreach ($file in $files) {
    $current++
    if ($current % 50 -eq 0) {
        Write-Host "  Processing file $current/$totalFiles..." -ForegroundColor Gray
    }
    
    try {
        $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $originalContent = $content
        
        # Replace package declarations
        $content = $content -creplace '(?m)^package fulguris\b', 'package com.xhub.browser'
        $content = $content -creplace '(?m)^package fulguris\.', 'package com.xhub.browser.'
        
        # Replace import statements
        $content = $content -creplace '(?m)^import fulguris\.', 'import com.xhub.browser.'
        
        # Replace fully-qualified class names in code (be conservative)
        $content = $content -creplace '\bfulguris\.Sponsorship\b', 'com.xhub.browser.Sponsorship'
        
        if ($content -ne $originalContent) {
            [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
            $updatedFiles++
        }
    }
    catch {
        Write-Host "  ERROR processing $($file.FullName): $_" -ForegroundColor Red
    }
}

Write-Host "  ✓ Updated $updatedFiles of $totalFiles files" -ForegroundColor Green

# Step 2: Update build.gradle
Write-Host "[2/5] Updating build.gradle..." -ForegroundColor Yellow

try {
    $buildGradle = "app\build.gradle"
    $content = [System.IO.File]::ReadAllText($buildGradle, [System.Text.Encoding]::UTF8)
    
    # Update all the specific lines
    $content = $content -replace "generated/source/locale/fulguris/locale", "generated/source/locale/com/xhub/browser/locale"
    $content = $content -replace "namespace = 'fulguris'", "namespace = 'com.xhub.browser'"
    $content = $content -creplace '"fulguris\.Sponsorship"', '"com.xhub.browser.Sponsorship"'
    $content = $content -replace 'package fulguris\.locale;', 'package com.xhub.browser.locale;'
    
    [System.IO.File]::WriteAllText($buildGradle, $content, [System.Text.Encoding]::UTF8)
    Write-Host "  ✓ Updated build.gradle" -ForegroundColor Green
}
catch {
    Write-Host "  ERROR updating build.gradle: $_" -ForegroundColor Red
}

# Step 3: Update AndroidManifest.xml
Write-Host "[3/5] Updating AndroidManifest.xml..." -ForegroundColor Yellow

try {
    $manifest = "app\src\main\AndroidManifest.xml"
    $content = [System.IO.File]::ReadAllText($manifest, [System.Text.Encoding]::UTF8)
    
    # Replace fulguris. with com.xhub.browser. in class references
    $content = $content -replace 'android:name="fulguris\.', 'android:name="com.xhub.browser.'
    $content = $content -replace 'android:targetActivity="fulguris\.', 'android:targetActivity="com.xhub.browser.'
    $content = $content -replace 'android:parentActivityName="fulguris\.', 'android:parentActivityName="com.xhub.browser.'
    
    [System.IO.File]::WriteAllText($manifest, $content, [System.Text.Encoding]::UTF8)
    Write-Host "  ✓ Updated AndroidManifest.xml" -ForegroundColor Green
}
catch {
    Write-Host "  ERROR updating AndroidManifest.xml: $_" -ForegroundColor Red
}

# Step 4: Update preference XML files
Write-Host "[4/5] Updating preference XML files..." -ForegroundColor Yellow

try {
    $prefFiles = Get-ChildItem -Path "app\src\main\res\xml" -Filter "*.xml" -File
    $prefUpdated = 0
    
    foreach ($file in $prefFiles) {
        $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $originalContent = $content
        
        # Replace fulguris. in value attributes
        $content = $content -replace 'value="fulguris\.', 'value="com.xhub.browser.'
        
        if ($content -ne $originalContent) {
            [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
            $prefUpdated++
        }
    }
    
    Write-Host "  ✓ Updated $prefUpdated preference files" -ForegroundColor Green
}
catch {
    Write-Host "  ERROR updating preference files: $_" -ForegroundColor Red
}

# Step 5: Move directory structure
Write-Host "[5/5] Moving directory structure..." -ForegroundColor Yellow

$sourceDirs = @(
    "app\src\main\java\fulguris",
    "app\src\test\java\fulguris",
    "app\src\androidTest\java\fulguris",
    "app\src\download\java\fulguris",
    "app\src\fdroid\java\fulguris",
    "app\src\playstore\java\fulguris"
)

$movedDirs = 0

foreach ($sourceDir in $sourceDirs) {
    if (Test-Path $sourceDir) {
        try {
            $parent = Split-Path $sourceDir -Parent
            $targetBase = Join-Path $parent "com"
            $targetXhub = Join-Path $targetBase "xhub"
            $targetFinal = Join-Path $targetXhub "browser"
            
            # Create target directory structure
            if (-not (Test-Path $targetBase)) {
                New-Item -ItemType Directory -Path $targetBase -Force | Out-Null
            }
            if (-not (Test-Path $targetXhub)) {
                New-Item -ItemType Directory -Path $targetXhub -Force | Out-Null
            }
            
            # Move the directory
            Write-Host "  Moving: $sourceDir → $targetFinal" -ForegroundColor Gray
            Move-Item -Path $sourceDir -Destination $targetFinal -Force -ErrorAction Stop
            $movedDirs++
        }
        catch {
            Write-Host "  ERROR moving $sourceDir : $_" -ForegroundColor Red
        }
    }
}

Write-Host "  ✓ Moved $movedDirs directory trees" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Package Rename Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  • Kotlin/Java files: $updatedFiles updated" -ForegroundColor White
Write-Host "  • build.gradle: updated" -ForegroundColor White
Write-Host "  • AndroidManifest.xml: updated" -ForegroundColor White
Write-Host "  • Preference files: updated" -ForegroundColor White
Write-Host "  • Directories: $movedDirs moved" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. .\gradlew.bat clean" -ForegroundColor White
Write-Host "  2. .\gradlew.bat assembleXhubFullDownloadDebug" -ForegroundColor White
Write-Host ""
