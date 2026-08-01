package com.xhub.browser.utils

import timber.log.Timber
import java.io.File

/**
 * Sanitizes filenames from untrusted sources (Content-Disposition headers, user input)
 * to prevent path traversal attacks and filesystem corruption.
 */
object FileNameSanitizer {

    // Characters that are illegal in filenames on Windows/Android
    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    // Reserved filenames on Windows (also avoid on Android for compatibility)
    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Sanitizes a filename to prevent path traversal and filesystem attacks.
     *
     * Security checks:
     * 1. Removes path separators (/, \)
     * 2. Removes parent directory references (.., .)
     * 3. Removes illegal characters
     * 4. Prevents reserved Windows filenames
     * 5. Enforces reasonable length limits
     * 6. Ensures the result stays within the target directory
     *
     * @param filename The untrusted filename to sanitize
     * @param maxLength Maximum allowed filename length (default 255)
     * @return Sanitized filename, or "download" if input is invalid
     */
    fun sanitize(filename: String?, maxLength: Int = 255): String {
        if (filename.isNullOrBlank()) {
            return "download"
        }

        var safe = filename.trim()

        // Remove any path components (extract just the filename)
        safe = safe.substringAfterLast('/')
            .substringAfterLast('\\')

        // Remove parent directory references
        safe = safe.replace("..", "")
            .replace("./", "")
            .replace(".\\", "")

        // Remove illegal characters
        safe = safe.replace(ILLEGAL_CHARS, "_")

        // Remove leading/trailing dots and spaces (Windows compatibility)
        safe = safe.trim('.', ' ')

        // Check for reserved names (case-insensitive)
        val nameWithoutExt = safe.substringBeforeLast('.', safe)
        if (nameWithoutExt.uppercase() in RESERVED_NAMES) {
            safe = "_$safe"
        }

        // Enforce length limit (leave room for extension)
        if (safe.length > maxLength) {
            val ext = safe.substringAfterLast('.', "")
            val name = safe.substringBeforeLast('.', safe)
            val maxNameLength = maxLength - ext.length - 1  // -1 for the dot
            safe = name.take(maxNameLength) + if (ext.isNotEmpty()) ".$ext" else ""
        }

        // Final safety check: if we ended up with an empty string, use default
        if (safe.isBlank()) {
            return "download"
        }

        return safe
    }

    /**
     * Verifies that a file path stays within the intended directory.
     * Prevents path traversal via symlinks or canonicalization tricks.
     *
     * @param targetDir The directory that should contain the file
     * @param filename The sanitized filename
     * @return true if the resolved path is safe, false if it escapes targetDir
     */
    fun isPathSafe(targetDir: File, filename: String): Boolean {
        return try {
            val targetFile = File(targetDir, filename)
            val canonicalTarget = targetFile.canonicalPath
            val canonicalDir = targetDir.canonicalPath

            // Ensure the resolved path is still inside the target directory
            val isSafe = canonicalTarget.startsWith(canonicalDir + File.separator) ||
                canonicalTarget == canonicalDir

            if (!isSafe) {
                Timber.w("Path traversal detected: $filename resolves outside $targetDir")
            }

            isSafe
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify path safety for: $filename")
            false
        }
    }

    /**
     * Sanitizes and verifies a filename is safe for the target directory.
     * Combines sanitize() + isPathSafe() for convenience.
     *
     * @param targetDir The directory that should contain the file
     * @param filename The untrusted filename
     * @return Sanitized filename if safe, or null if path traversal detected
     */
    fun sanitizeAndVerify(targetDir: File, filename: String?): String? {
        val safe = sanitize(filename)
        return if (isPathSafe(targetDir, safe)) safe else null
    }
}
