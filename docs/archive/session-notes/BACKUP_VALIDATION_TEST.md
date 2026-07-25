# Backup Exclusion Validation Test

## Purpose
Verify that domain-specific preferences are correctly excluded from backups.

## Background
Domain preferences are stored with the prefix `[Domain]` followed by the reversed domain name.

**Examples:**
- `example.com` → `[Domain]com.example`
- `www.google.com` → `[Domain]com.google.www`
- `github.com` → `[Domain]com.github`

## Test Procedure

### Prerequisites
- ADB installed and device connected
- Fulguris app installed
- Backup enabled on device

### Step 1: Create Domain Override

1. Open Fulguris browser
2. Navigate to a test domain (e.g., `example.com`)
3. Open domain settings (long-press on URL bar or via menu)
4. Enable at least one override (e.g., "Allow mixed content" or "Desktop mode")
5. Save and exit

### Step 2: Verify Preference File Created

```bash
# List shared preferences files
adb shell "run-as <package-name> ls -la /data/data/<package-name>/shared_prefs/"

# Look for files matching pattern: [Domain]*.xml
# Example: [Domain]com.example.xml
```

**Expected:** You should see a file like `[Domain]com.example.xml`

### Step 3: Trigger Backup

```bash
# Enable backup manager
adb shell bmgr enable true

# Trigger backup for the app
adb shell bmgr backupnow <package-name>

# Wait for backup to complete
adb shell bmgr list transports
```

### Step 4: Extract and Inspect Backup

```bash
# For Android 6-11 (full backup)
adb shell bmgr fullbackup <package-name>

# For Android 12+ (data extraction)
# Backup is automatic, check with:
adb shell dumpsys backup
```

### Step 5: Verify Exclusion

```bash
# Uninstall app (this removes all data)
adb uninstall <package-name>

# Reinstall app
adb install <path-to-apk>

# Restore from backup
adb shell bmgr restore <package-name>

# Check if domain preferences were restored
adb shell "run-as <package-name> ls -la /data/data/<package-name>/shared_prefs/"
```

**Expected Result:** 
- ✅ General app preferences SHOULD be restored
- ❌ Domain-specific preferences (files starting with `[Domain]`) should NOT be restored
- ❌ The `[Domain]com.example.xml` file should NOT exist after restore

### Step 6: Verify Override is Gone

1. Open Fulguris browser
2. Navigate to the same test domain (e.g., `example.com`)
3. Open domain settings
4. Check override status

**Expected:** All overrides should be OFF (default state), confirming the domain preferences were not backed up/restored.

## Alternative Quick Test

### Using Backup Extraction Tool

```bash
# Extract backup to local file
adb backup -f backup.ab <package-name>

# Convert to tar (requires Android Backup Extractor tool)
java -jar abe.jar unpack backup.ab backup.tar

# Extract tar
tar -xvf backup.tar

# Inspect shared_prefs directory
ls -la apps/<package-name>/sp/

# Verify no files starting with [Domain] are present
ls -la apps/<package-name>/sp/[Domain]*
```

**Expected:** Command should return "No such file or directory"

## Validation Checklist

- [ ] Domain preference file created with correct `[Domain]` prefix
- [ ] Backup triggered successfully
- [ ] After restore, domain preference file does NOT exist
- [ ] After restore, domain overrides are reset to defaults
- [ ] General app preferences ARE restored (theme, settings, etc.)
- [ ] No files matching `[Domain]*` pattern in backup archive

## Troubleshooting

### If domain preferences ARE being backed up:

1. **Check Android version:**
   - Android 6-11: Uses `backup_rules.xml`
   - Android 12+: Uses `data_extraction_rules.xml`

2. **Verify XML syntax:**
   ```xml
   <exclude domain="sharedpref" path="[Domain]" />
   ```

3. **Check file naming:**
   - Ensure DomainPreferences.kt uses `[Domain]` prefix
   - Verify with: `adb shell "run-as <package-name> ls shared_prefs/"`

4. **Rebuild app:**
   - Clean build: `./gradlew clean`
   - Rebuild: `./gradlew assembleDebug`
   - Reinstall completely

### Common Issues

- **Brackets in path:** Android backup system supports `[` and `]` in path patterns
- **Wildcard matching:** The path parameter matches prefixes, so `[Domain]` matches all files starting with `[Domain]`
- **Case sensitivity:** File names are case-sensitive

## Success Criteria

✅ **PASS:** Domain preferences are excluded from backup and not restored  
❌ **FAIL:** Domain preferences are included in backup or restored after reinstall

## Notes

- This test should be run on both Android 11 (or lower) and Android 12+ devices
- Test with multiple domains to ensure pattern matching works correctly
- Verify both cloud backup and device-to-device transfer scenarios
