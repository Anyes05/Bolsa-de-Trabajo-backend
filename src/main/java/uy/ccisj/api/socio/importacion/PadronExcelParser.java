package uy.ccisj.api.socio.importacion;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class PadronExcelParser {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("es-UY"));
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uu"));

    public record RawRow(
            int filaExcel,
            String razonSocial,
            String direccion,
            String celular,
            String telefono,
            String localidad,
            String giro,
            String mail,
            String rut,
            String bps,
            String inicio,
            String fechaAfiliacion) {}

    private PadronExcelParser() {}

    public static List<RawRow> parse(InputStream input) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("El Excel no tiene hojas");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("El Excel no tiene encabezados");
            }
            Map<String, Integer> columns = mapHeaders(headerRow);
            if (!columns.containsKey("razon")) {
                throw new IllegalArgumentException(
                        "No se encontró la columna de razón social. Revisá que la primera fila tenga los títulos.");
            }
            List<RawRow> rows = new ArrayList<>();
            int last = sheet.getLastRowNum();
            for (int index = headerRow.getRowNum() + 1; index <= last; index++) {
                Row row = sheet.getRow(index);
                if (row == null || isEmpty(row)) {
                    continue;
                }
                rows.add(new RawRow(
                        index + 1,
                        cell(row, columns.get("razon")),
                        cell(row, columns.get("direccion")),
                        cell(row, columns.get("celular")),
                        cell(row, columns.get("telefono")),
                        cell(row, columns.get("localidad")),
                        cell(row, columns.get("giro")),
                        cell(row, columns.get("mail")),
                        cell(row, columns.get("rut")),
                        cell(row, columns.get("bps")),
                        cell(row, columns.get("inicio")),
                        cell(row, columns.get("afiliacion"))));
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No se pudo leer el Excel: " + exception.getMessage(), exception);
        }
    }

    public static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('º', ' ')
                .replace('°', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return plain.replaceAll("\\s+", " ");
    }

    public static LocalDate parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATES) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // siguiente formato
            }
        }
        if (text.matches("\\d{4}")) {
            return LocalDate.of(Integer.parseInt(text), 1, 1);
        }
        return null;
    }

    private static Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> columns = new HashMap<>();
        short last = headerRow.getLastCellNum();
        for (int index = 0; index < last; index++) {
            String header = normalizeHeader(cell(headerRow, index));
            if (header.isEmpty()) {
                continue;
            }
            if (containsAny(header, "razon social", "nombre o razon", "nombre razon") || header.equals("nombre")) {
                columns.putIfAbsent("razon", index);
            } else if (header.contains("direccion")) {
                columns.putIfAbsent("direccion", index);
            } else if (header.contains("celular")) {
                columns.putIfAbsent("celular", index);
            } else if (header.equals("telefono") || header.startsWith("telefono ")) {
                columns.putIfAbsent("telefono", index);
            } else if (header.contains("localidad")) {
                columns.putIfAbsent("localidad", index);
            } else if (header.equals("giro") || header.contains("rubro")) {
                columns.putIfAbsent("giro", index);
            } else if (header.equals("mail") || header.contains("correo") || header.equals("email")) {
                columns.putIfAbsent("mail", index);
            } else if (header.equals("rut")) {
                columns.putIfAbsent("rut", index);
            } else if (header.contains("bps")) {
                columns.putIfAbsent("bps", index);
            } else if (header.equals("inicio")) {
                columns.putIfAbsent("inicio", index);
            } else if (header.contains("afiliacion")) {
                columns.putIfAbsent("afiliacion", index);
            }
        }
        return columns;
    }

    private static boolean containsAny(String header, String... options) {
        for (String option : options) {
            if (header.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(Row row) {
        short last = row.getLastCellNum();
        for (int index = 0; index < last; index++) {
            if (!cell(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(Row row, Integer index) {
        if (row == null || index == null) {
            return "";
        }
        return cell(row, index.intValue());
    }

    private static String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
        } catch (Exception ignored) {
            // formato no fecha
        }
        return FORMATTER.formatCellValue(cell).trim();
    }
}
