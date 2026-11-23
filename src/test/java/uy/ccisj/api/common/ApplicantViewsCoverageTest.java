package uy.ccisj.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Formateos compartidos de las vistas de postulantes.
 */
class ApplicantViewsCoverageTest {

    @Test
    void separaRubrosIgnorandoVaciosYBlancos() {
        assertEquals(List.of(), ApplicantViews.splitRubros(null));
        assertEquals(List.of(), ApplicantViews.splitRubros("   "));
        assertEquals(List.of("Comercio", "Industria"), ApplicantViews.splitRubros("Comercio|| |Industria"));
    }

    @Test
    void inicialesUsanLasDosPrimerasPalabras() {
        assertEquals("", ApplicantViews.initials(null));
        assertEquals("", ApplicantViews.initials("  "));
        assertEquals("AP", ApplicantViews.initials("  ana   perez gomez"));
        assertEquals("Ñ", ApplicantViews.initials("ñandu"));
    }

    @Test
    void metadatosDelCvSegunTamano() {
        assertEquals("PDF oficial", ApplicantViews.cvMeta(2048, true));
        assertEquals("PDF oficial", ApplicantViews.cvMeta(0, false));
        assertEquals("PDF oficial", ApplicantViews.cvMeta(-5, false));
        assertEquals("PDF oficial · 1 KB", ApplicantViews.cvMeta(100, false));
        assertEquals("PDF oficial · 2 KB", ApplicantViews.cvMeta(2048, false));
        assertEquals("PDF oficial · 1024 KB", ApplicantViews.cvMeta(1024 * 1024 - 1, false));
        assertEquals("PDF oficial · 1.0 MB", ApplicantViews.cvMeta(1024 * 1024, false));
        assertEquals("PDF oficial · 2.5 MB", ApplicantViews.cvMeta(5 * 1024 * 1024 / 2, false));
    }
}
