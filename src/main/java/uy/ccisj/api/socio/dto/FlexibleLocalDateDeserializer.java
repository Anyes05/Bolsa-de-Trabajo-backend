package uy.ccisj.api.socio.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            // También se acepta el formato que muestra el navegador en español.
        }
        try {
            return LocalDate.parse(value, DAY_MONTH_YEAR);
        } catch (DateTimeParseException ignored) {
            context.reportInputMismatch(LocalDate.class, "Fecha inválida: %s", value);
            return null;
        }
    }
}
