# Move fulguris directories to com\xhub\browser

$ErrorActionPreference = "Stop"

Write-Host "Moving directory structures..." -ForegroundColor Yellow

$moved = 0

# Define all source directories
$sourceDirs = @(
    "app\src\main\java\fulguris",
    "app\src\test\java\fulguris",
    "app\src\download\java\fulguris",
    "app\src\fdroid\java\fulguris",
    "app\src\playstore\java\fulguris"
)

foreach ($sourceDir in $sourceDirs) {
    if (Test-Path $sourceDir) {
        Write-Host "  Processing: $sourceDir" -ForegroundColor Gray
        
        $parent = Split-Path $sourceDir -Parent
        $comDir = Join-Path $parent "com"
        $xhubDir = Join-Path $comDir "xhub"
        $browserDir = Join-Path $xhubDir "browser"
        
        # Create com directory if needed
        if (-not (Test-Path $comDir)) {
            New-Item -ItemType Directory -Path $comDir -Force | Out-Null
            Write-Host "    Created: $comDir" -ForegroundColor Gray
        }
        
        # Create xhub directory if needed
        if (-not (Test-Path $xhubDir)) {
            New-Item -ItemType Directory -Path $xhubDir -Force | Out-Null
            Write-Host "    Created: $xhubDir" -ForegroundColor Gray
        }
        
        # Move fulguris to browser
        Write-Host "    Moving to: $browserDir" -ForegroundColor Gray
        Move-Item -Path $sourceDir -Destination $browserDir -Force
        Write-Host "    ✓ Moved successfully" -ForegroundColor Green
        $moved++
    }
    else {
        Write-Host "  Skipping: $sourceDir (does not exist)" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "✓ Moved $moved directory trees" -ForegroundColor Green
