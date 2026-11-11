package uy.ccisj.api.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MailTemplateTest {

    @Test
    void generaHtmlConMarcaYBoton() {
        MailTemplate.Content content = MailTemplate.build(
                "Confirmá tu permanencia",
                "Hola, Ana:",
                List.of("Hace más de 2 meses que no ingresás."),
                List.of(new MailTemplate.Detail("Fecha límite", "15/11/2026")),
                new MailTemplate.Cta("Confirmar permanencia", "https://ccisj.cloud-ip.cc/permanencia?token=abc"),
                "Pie de prueba");

        assertTrue(content.html().contains("Centro Comercial e Industrial de San José"));
        assertTrue(content.html().contains("Confirmar permanencia"));
        assertTrue(content.html().contains("https://ccisj.cloud-ip.cc/permanencia?token=abc"));
        assertTrue(content.plain().contains("Fecha límite: 15/11/2026"));
    }

    @Test
    void escapaHtmlPeligrosoEnTituloYCuerpo() {
        MailTemplate.Content content = MailTemplate.build(
                "<script>alert(1)</script>",
                "Hola <b>x</b>:",
                List.of("Empresa & Co <img src=x onerror=alert(1)>"),
                List.of(),
                null,
                null);

        assertFalse(content.html().contains("<script>"));
        assertTrue(content.html().contains("&lt;script&gt;"));
        assertTrue(content.html().contains("Empresa &amp; Co"));
        assertTrue(content.html().contains("&lt;img"));
    }
}
