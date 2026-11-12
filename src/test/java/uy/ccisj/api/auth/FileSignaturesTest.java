package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileSignaturesTest {
    @Test
    void detectaImagenesPorFirma() {
        assertEquals("image/png", FileSignatures.detectImageMime(FileSignatures.PNG_HEADER));
        assertEquals("image/jpeg", FileSignatures.detectImageMime(FileSignatures.JPEG_HEADER));
        assertEquals("image/webp", FileSignatures.detectImageMime(new byte[] {
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertNull(FileSignatures.detectImageMime(new byte[] {1, 2, 3}));
    }

    @Test
    void detectaPdfPorCabecera() {
        assertEquals("application/pdf", FileSignatures.detectDocumentMime("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII)));
        assertNull(FileSignatures.detectDocumentMime("not-a-pdf".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void claveProvisionalEsAleatoriaYCumplePolitica() {
        String one = PasswordPolicy.generateProvisional("Distribuidora Fernandez");
        String two = PasswordPolicy.generateProvisional("Distribuidora Fernandez");
        assertTrue(PasswordPolicy.isValid(one));
        assertTrue(PasswordPolicy.isValid(two));
        assertTrue(one.length() >= 12);
        assertTrue(!one.toLowerCase().contains("distribuidora"));
        assertTrue(!one.equals(two));
    }
}
