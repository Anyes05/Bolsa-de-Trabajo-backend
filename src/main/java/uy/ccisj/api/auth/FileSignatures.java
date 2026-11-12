package uy.ccisj.api.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Detecta el tipo real del archivo por firma, no por el Content-Type del navegador.
 */
public final class FileSignatures {
    public static final byte[] PNG_HEADER = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    public static final byte[] JPEG_HEADER = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    public static final byte[] PDF_HEADER = new byte[] {'%', 'P', 'D', 'F'};

    private FileSignatures() {}

    public static String detectImageMime(byte[] bytes) {
        if (startsWith(bytes, PNG_HEADER)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG_HEADER)) {
            return "image/jpeg";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        return null;
    }

    public static String detectDocumentMime(byte[] bytes) {
        if (startsWith(bytes, PDF_HEADER)) {
            return "application/pdf";
        }
        if (isDocx(bytes)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return null;
    }

    static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes != null && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean isDocx(byte[] bytes) {
        if (bytes == null || bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < 40) {
                String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
                if ("[content_types].xml".equals(name) || name.startsWith("word/")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }
}
