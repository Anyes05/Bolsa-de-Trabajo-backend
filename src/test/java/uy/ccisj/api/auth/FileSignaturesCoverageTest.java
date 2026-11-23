package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Deteccion por firma de WEBP y DOCX, incluidos los archivos que se hacen pasar por ellos.
 */
class FileSignaturesCoverageTest {
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static byte[] zip(String... nombres) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (String nombre : nombres) {
                zip.putNextEntry(new ZipEntry(nombre));
                zip.write('x');
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private static byte[] riff(char w, char e, char b, char p) {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, (byte) w, (byte) e, (byte) b, (byte) p};
    }

    @Test
    void detectaWebpSoloConCabeceraRiffWebpCompleta() {
        assertEquals("image/webp", FileSignatures.detectImageMime(riff('W', 'E', 'B', 'P')));
        assertNull(FileSignatures.detectImageMime(riff('W', 'A', 'V', 'E')));
        assertNull(FileSignatures.detectImageMime(riff('X', 'E', 'B', 'P')));
        assertNull(FileSignatures.detectImageMime(riff('W', 'X', 'B', 'P')));
        assertNull(FileSignatures.detectImageMime(riff('W', 'E', 'X', 'P')));
        assertNull(FileSignatures.detectImageMime(riff('W', 'E', 'B', 'X')));
        assertNull(FileSignatures.detectImageMime(new byte[] {'R', 'I', 'F', 'F'}), "demasiado corto");
        for (int posicion = 0; posicion < 4; posicion++) {
            byte[] alterado = riff('W', 'E', 'B', 'P');
            alterado[posicion] = 'Z';
            assertNull(FileSignatures.detectImageMime(alterado), "RIFF alterado en " + posicion);
        }
        assertNull(FileSignatures.detectImageMime(null));
    }

    @Test
    void detectaDocxPorContentTypesOCarpetaWord() throws IOException {
        assertEquals(DOCX, FileSignatures.detectDocumentMime(zip("[Content_Types].xml")));
        assertEquals(DOCX, FileSignatures.detectDocumentMime(zip("docProps/app.xml", "WORD/document.xml")));
    }

    @Test
    void zipSinEstructuraWordNoEsDocx() throws IOException {
        assertNull(FileSignatures.detectDocumentMime(zip("foto.png", "xl/workbook.xml")));
        assertNull(FileSignatures.detectDocumentMime(zip()), "zip vacio");
    }

    @Test
    void soloRevisaLasPrimeras40Entradas() throws IOException {
        String[] nombres = new String[42];
        for (int index = 0; index < 41; index++) {
            nombres[index] = "relleno/" + index + ".txt";
        }
        nombres[41] = "word/document.xml";
        assertNull(FileSignatures.detectDocumentMime(zip(nombres)));
    }

    @Test
    void zipTruncadoOCabeceraFalsaNoEsDocx() {
        assertNull(FileSignatures.detectDocumentMime(new byte[] {'P', 'K', 3, 4, 0, 0}), "zip truncado");
        // Cabecera local valida que declara un nombre de 100 bytes que no existe: ZipInputStream lanza EOFException.
        byte[] cabeceraRota = new byte[30];
        cabeceraRota[0] = 'P';
        cabeceraRota[1] = 'K';
        cabeceraRota[2] = 3;
        cabeceraRota[3] = 4;
        cabeceraRota[26] = 100;
        assertNull(FileSignatures.detectDocumentMime(cabeceraRota), "nombre de entrada truncado");
        assertNull(FileSignatures.detectDocumentMime(new byte[] {'P', 'X', 3, 4}));
        assertNull(FileSignatures.detectDocumentMime(new byte[] {'X', 'K', 3, 4}));
        assertNull(FileSignatures.detectDocumentMime(new byte[] {'P', 'K'}), "demasiado corto");
        assertNull(FileSignatures.detectDocumentMime(null));
    }

    @Test
    void startsWithComparaByteAByte() {
        assertFalse(FileSignatures.startsWith(null, FileSignatures.PDF_HEADER));
        assertFalse(FileSignatures.startsWith(new byte[] {'%', 'P'}, FileSignatures.PDF_HEADER));
        assertFalse(FileSignatures.startsWith(new byte[] {'%', 'P', 'D', 'X'}, FileSignatures.PDF_HEADER));
        assertEquals("application/pdf", FileSignatures.detectDocumentMime(new byte[] {'%', 'P', 'D', 'F', '-', '1'}));
    }
}
