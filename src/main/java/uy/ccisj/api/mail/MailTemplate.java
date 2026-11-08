package uy.ccisj.api.mail;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla HTML + texto plano para correos CCISJ (compatible con clientes de mail).
 */
public final class MailTemplate {
    private MailTemplate() {}

    public record Detail(String label, String value) {}

    public record Cta(String label, String url) {}

    public record Content(String html, String plain) {}

    public static Content build(
            String title,
            String greeting,
            List<String> paragraphs,
            List<Detail> details,
            Cta cta,
            String footerNote) {
        List<String> safeParagraphs = paragraphs == null ? List.of() : paragraphs.stream()
                .filter(p -> p != null && !p.isBlank())
                .toList();
        List<Detail> safeDetails = details == null ? List.of() : details.stream()
                .filter(d -> d != null && d.value() != null && !d.value().isBlank())
                .toList();

        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#eef2f0;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#243028;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#eef2f0;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #d8e2db;">
                          <tr>
                            <td style="background:#0a7a3e;padding:22px 28px;">
                              <p style="margin:0;color:#d8f5e4;font-size:12px;letter-spacing:.06em;text-transform:uppercase;font-weight:700;">Centro Comercial e Industrial de San José</p>
                              <h1 style="margin:8px 0 0;color:#ffffff;font-size:22px;line-height:1.3;font-weight:700;">%s</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px;">
                              %s
                              %s
                              %s
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 28px 24px;background:#f7faf8;border-top:1px solid #e4ece7;">
                              <p style="margin:0;color:#66756c;font-size:12px;line-height:1.5;">%s</p>
                              <p style="margin:10px 0 0;color:#8a9790;font-size:11px;line-height:1.45;">Artigas 532, San José de Mayo, Uruguay · CCISJ</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(title),
                escape(title),
                greetingBlock(greeting),
                paragraphsBlock(safeParagraphs),
                detailsBlock(safeDetails),
                ctaBlock(cta),
                escape(footerNote == null || footerNote.isBlank()
                        ? "Este es un correo automático del sistema de Bolsa de Trabajo del CCISJ."
                        : footerNote));

        String plain = buildPlain(title, greeting, safeParagraphs, safeDetails, cta, footerNote);
        return new Content(html, plain);
    }

    public static Content simple(String title, String greeting, String... paragraphs) {
        return build(title, greeting, List.of(paragraphs), List.of(), null, null);
    }

    private static String greetingBlock(String greeting) {
        if (greeting == null || greeting.isBlank()) {
            return "";
        }
        return "<p style=\"margin:0 0 16px;font-size:16px;line-height:1.5;font-weight:600;color:#1f2f26;\">"
                + escape(greeting) + "</p>";
    }

    private static String paragraphsBlock(List<String> paragraphs) {
        if (paragraphs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String paragraph : paragraphs) {
            sb.append("<p style=\"margin:0 0 14px;font-size:15px;line-height:1.6;color:#334139;\">")
                    .append(escape(paragraph))
                    .append("</p>");
        }
        return sb.toString();
    }

    private static String detailsBlock(List<Detail> details) {
        if (details.isEmpty()) {
            return "";
        }
        StringBuilder rows = new StringBuilder();
        for (Detail detail : details) {
            rows.append("""
                    <tr>
                      <td style="padding:10px 0;border-bottom:1px solid #e7eee9;color:#66756c;font-size:13px;width:42%%;vertical-align:top;">%s</td>
                      <td style="padding:10px 0;border-bottom:1px solid #e7eee9;color:#1f2f26;font-size:14px;font-weight:700;vertical-align:top;">%s</td>
                    </tr>
                    """.formatted(escape(detail.label()), escape(detail.value())));
        }
        return """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:8px 0 20px;padding:4px 16px;background:#f3f8f5;border:1px solid #d9e6de;border-radius:10px;">
                  %s
                </table>
                """.formatted(rows);
    }

    private static String ctaBlock(Cta cta) {
        if (cta == null || cta.url() == null || cta.url().isBlank()) {
            return "";
        }
        String label = cta.label() == null || cta.label().isBlank() ? "Abrir enlace" : cta.label();
        return """
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:8px 0 18px;">
                  <tr>
                    <td style="border-radius:8px;background:#0a7a3e;">
                      <a href="%s" style="display:inline-block;padding:12px 20px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700;">%s</a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 12px;font-size:12px;line-height:1.5;color:#7a8780;word-break:break-all;">Si el botón no funciona, copiá este enlace:<br>%s</p>
                """.formatted(escapeAttr(cta.url()), escape(label), escape(cta.url()));
    }

    private static String buildPlain(
            String title,
            String greeting,
            List<String> paragraphs,
            List<Detail> details,
            Cta cta,
            String footerNote) {
        List<String> lines = new ArrayList<>();
        lines.add("Centro Comercial e Industrial de San José (CCISJ)");
        lines.add(title == null ? "" : title.trim());
        lines.add("");
        if (greeting != null && !greeting.isBlank()) {
            lines.add(greeting.trim());
            lines.add("");
        }
        for (String paragraph : paragraphs) {
            lines.add(paragraph.trim());
            lines.add("");
        }
        if (!details.isEmpty()) {
            for (Detail detail : details) {
                lines.add(detail.label() + ": " + detail.value());
            }
            lines.add("");
        }
        if (cta != null && cta.url() != null && !cta.url().isBlank()) {
            if (cta.label() != null && !cta.label().isBlank()) {
                lines.add(cta.label() + ":");
            }
            lines.add(cta.url().trim());
            lines.add("");
        }
        lines.add("---");
        lines.add(footerNote == null || footerNote.isBlank()
                ? "Este es un correo automático del sistema de Bolsa de Trabajo del CCISJ."
                : footerNote.trim());
        lines.add("Artigas 532, San José de Mayo, Uruguay");
        return String.join("\n", lines).trim() + "\n";
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
