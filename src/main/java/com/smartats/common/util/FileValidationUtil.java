package com.smartats.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * File validation utility
 * Verifies the real file type via the file header (magic number) to prevent file-type spoofing attacks
 */
@Slf4j
public class FileValidationUtil {

    /**
     * Magic numbers (file header signatures) of common file types
     * Key: file extension
     * Value: hexadecimal file-header signature
     */
    private static final Map<String, String> FILE_SIGNATURES = new HashMap<>();

    static {
        // PDF: %PDF (25 50 44 46)
        FILE_SIGNATURES.put(".pdf", "25 50 44 46");

        // DOC (Microsoft Word 97-2003): D0 CF 11 E0 A1 B1 1A E1
        FILE_SIGNATURES.put(".doc", "D0 CF 11 E0");

        // DOCX (Microsoft Word 2007+): PK (50 4B 03 04) - ZIP format
        FILE_SIGNATURES.put(".docx", "50 4B 03 04");
    }

    /**
     * Get the real MIME type of a file (byte array version, recommended)
     *
     * @param fileBytes the file byte array
     * @param originalFilename the original file name (used as a hint)
     * @return the real MIME type, or null when it cannot be recognised
     */
    public static String detectRealMimeType(byte[] fileBytes, String originalFilename) {
        if (fileBytes == null || fileBytes.length < 4) {
            return null;
        }
        byte[] header = fileBytes;
        return parseMimeType(header, originalFilename);
    }

    /**
     * Get the real MIME type of a file
     * Determined from the file content rather than the extension or the Content-Type
     *
     * @param inputStream the file input stream
     * @param originalFilename the original file name (used as a hint)
     * @return the real MIME type, or null when it cannot be recognised
     */
    public static String detectRealMimeType(InputStream inputStream, String originalFilename) {
        try {
            byte[] header = readFileHeader(inputStream);

            return parseMimeType(header, originalFilename);

        } catch (IOException e) {
            log.error("Failed to read the file header", e);
            return null;
        }
    }

    /**
     * Parse the MIME type from the file header bytes (shared internal logic)
     */
    private static String parseMimeType(byte[] header, String originalFilename) {
        // Check whether it is a PDF
        if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
            return "application/pdf";
        }

        // Check whether it is DOC (legacy Word)
        if (header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF && header[2] == 0x11 && header[3] == (byte) 0xE0) {
            return "application/msword";
        }

        // Check whether it is DOCX (modern Word, which is really a ZIP archive)
        if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return "application/zip";
        }

        log.warn("Unrecognised file type, file header: 0x{} 0x{} 0x{} 0x{}",
                Integer.toHexString(header[0] & 0xFF),
                Integer.toHexString(header[1] & 0xFF),
                Integer.toHexString(header[2] & 0xFF),
                Integer.toHexString(header[3] & 0xFF));
        return null;
    }

    /**
     * Validate that the file matches its declared type (byte array version, recommended as it avoids stream mark/reset issues)
     *
     * @param fileBytes the file byte array
     * @param declaredContentType the declared Content-Type
     * @param originalFilename the original file name
     * @return true if the real file type matches the declared one
     */
    public static boolean validateFileType(byte[] fileBytes, String declaredContentType, String originalFilename) {
        String realMimeType = detectRealMimeType(fileBytes, originalFilename);
        return checkMimeMatch(realMimeType, declaredContentType, originalFilename);
    }

    /**
     * Validate that the file matches its declared type
     *
     * @param inputStream the file input stream
     * @param declaredContentType the declared Content-Type
     * @param originalFilename the original file name
     * @return true if the real file type matches the declared one
     */
    public static boolean validateFileType(InputStream inputStream, String declaredContentType, String originalFilename) {
        String realMimeType = detectRealMimeType(inputStream, originalFilename);

        if (realMimeType == null) {
            return false;
        }

        return checkMimeMatch(realMimeType, declaredContentType, originalFilename);
    }

    private static boolean checkMimeMatch(String realMimeType, String declaredContentType, String originalFilename) {
        if (realMimeType == null) {
            return false;
        }
        boolean isValid = switch (declaredContentType) {
            case "application/pdf" -> realMimeType.equals("application/pdf");
            case "application/msword" -> realMimeType.equals("application/msword");
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    realMimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> false;
        };
        if (!isValid) {
            log.warn("File type mismatch: declared={}, real={}, fileName={}", declaredContentType, realMimeType, originalFilename);
        }
        return isValid;
    }

    /**
     * Read the file header (the first 8 bytes), wrapping the stream in a BufferedInputStream to support mark/reset
     */
    private static byte[] readFileHeader(InputStream inputStream) throws IOException {
        java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(inputStream);
        byte[] header = new byte[8];
        buffered.mark(8);
        int bytesRead = buffered.read(header);
        buffered.reset();

        if (bytesRead < 4) {
            throw new IOException("The file is too small to read its header");
        }

        return header;
    }

    /**
     * Sanitize a file name to prevent path traversal attacks
     * - Strip path separators
     * - Strip special characters
     * - Limit the file name length
     *
     * @param filename the original file name
     * @return the sanitized file name
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed";
        }

        // Strip path information (keep only the file name part)
        String sanitized = filename.replaceAll(".*/", "")
                                   .replaceAll(".*\\\\", "");

        // Strip dangerous characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", ""); // control characters
        sanitized = sanitized.replaceAll("[<>:\"|?*]", ""); // characters not allowed on Windows
        sanitized = sanitized.replaceAll("\\.\\.", ""); // prevent path traversal

        // Limit the file name length (255 bytes is the limit on most file systems)
        if (sanitized.length() > 200) {
            String extension = "";
            int dotIndex = sanitized.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = sanitized.substring(dotIndex);
                sanitized = sanitized.substring(0, 190) + extension;
            } else {
                sanitized = sanitized.substring(0, 200);
            }
        }

        if (sanitized.isEmpty()) {
            return "unnamed";
        }

        log.debug("Sanitized file name: {} -> {}", filename, sanitized);
        return sanitized;
    }
}
