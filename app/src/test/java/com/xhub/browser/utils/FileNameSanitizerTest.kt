package com.xhub.browser.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileNameSanitizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `normal filename is unchanged`() {
        assertEquals("document.pdf", FileNameSanitizer.sanitize("document.pdf"))
        assertEquals("photo_2024.jpg", FileNameSanitizer.sanitize("photo_2024.jpg"))
    }

    @Test
    fun `path separators are removed`() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../etc/passwd"))
        assertEquals("malicious.exe", FileNameSanitizer.sanitize("..\\..\\windows\\system32\\malicious.exe"))
        assertEquals("test.txt", FileNameSanitizer.sanitize("/absolute/path/test.txt"))
    }

    @Test
    fun `parent directory references are removed`() {
        assertEquals("file.txt", FileNameSanitizer.sanitize("../file.txt"))
        assertEquals("file.txt", FileNameSanitizer.sanitize("../../file.txt"))
        assertEquals("file.txt", FileNameSanitizer.sanitize("./file.txt"))
    }

    @Test
    fun `illegal characters are replaced with underscore`() {
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file:name.txt"))
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file*name.txt"))
        assertEquals("file_name.txt", FileNameSanitizer.sanitize("file?name.txt"))
        assertEquals("file_name_.txt", FileNameSanitizer.sanitize("file<name>.txt"))
    }

    @Test
    fun `reserved Windows names are prefixed`() {
        assertEquals("_CON.txt", FileNameSanitizer.sanitize("CON.txt"))
        assertEquals("_PRN.txt", FileNameSanitizer.sanitize("PRN.txt"))
        assertEquals("_AUX.log", FileNameSanitizer.sanitize("AUX.log"))
        assertEquals("_COM1.dat", FileNameSanitizer.sanitize("COM1.dat"))
    }

    @Test
    fun `leading and trailing dots are removed`() {
        assertEquals("file.txt", FileNameSanitizer.sanitize(".file.txt"))
        assertEquals("file.txt", FileNameSanitizer.sanitize("file.txt."))
        assertEquals("file.txt", FileNameSanitizer.sanitize("...file.txt..."))
    }

    @Test
    fun `long filenames are truncated`() {
        val longName = "a".repeat(300) + ".txt"
        val sanitized = FileNameSanitizer.sanitize(longName)
        assertTrue(sanitized.length <= 255)
        assertTrue(sanitized.endsWith(".txt"))
    }

    @Test
    fun `null and blank filenames return default`() {
        assertEquals("download", FileNameSanitizer.sanitize(null))
        assertEquals("download", FileNameSanitizer.sanitize(""))
        assertEquals("download", FileNameSanitizer.sanitize("   "))
    }

    @Test
    fun `completely invalid filename returns default`() {
        assertEquals("download", FileNameSanitizer.sanitize("..."))
        assertEquals("download", FileNameSanitizer.sanitize("///"))
        assertEquals("___", FileNameSanitizer.sanitize("***"))
    }

    @Test
    fun `isPathSafe detects path traversal`() {
        val dir = tempFolder.newFolder("downloads")

        // Safe paths
        assertTrue(FileNameSanitizer.isPathSafe(dir, "file.txt"))
        assertTrue(FileNameSanitizer.isPathSafe(dir, "subdir/file.txt"))

        // After sanitization, traversal attempts resolve inside the directory
        val sanitized = FileNameSanitizer.sanitize("../../etc/passwd")
        assertTrue(FileNameSanitizer.isPathSafe(dir, sanitized))

        // A filename that would resolve outside the directory must be rejected
        assertFalse(FileNameSanitizer.isPathSafe(dir, "../outside.txt"))
    }

    @Test
    fun `sanitizeAndVerify rejects traversal attempts`() {
        val dir = tempFolder.newFolder("downloads")

        // Normal file is accepted
        assertNotNull(FileNameSanitizer.sanitizeAndVerify(dir, "document.pdf"))

        // Traversal attempts are sanitized and accepted (path components removed)
        val result = FileNameSanitizer.sanitizeAndVerify(dir, "../../etc/passwd")
        assertNotNull(result)
        assertEquals("passwd", result)
    }

    @Test
    fun `real world Content-Disposition attacks`() {
        // Attack 1: Path traversal
        assertEquals("malware.exe",
            FileNameSanitizer.sanitize("..\\..\\..\\windows\\system32\\malware.exe"))

        // Attack 2: Null bytes (truncation attack)
        assertEquals("safe.txt_evil.exe",
            FileNameSanitizer.sanitize("safe.txt\u0000evil.exe"))

        // Attack 3: Reserved name
        assertEquals("_CON", FileNameSanitizer.sanitize("CON"))

        // Attack 4: Hidden file with traversal
        assertEquals("bashrc", FileNameSanitizer.sanitize("../../home/user/.bashrc"))
    }
}
