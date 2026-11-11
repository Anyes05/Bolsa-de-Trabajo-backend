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
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

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
            String fechaAfiliacion,
            boolean inactivo) {}

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
                        cell(row, columns.get("afiliacion")),
                        isInactiveRow(row)));
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

    static boolean isInactiveRow(Row row) {
        if (row == null) {
            return false;
        }
        Workbook workbook = row.getSheet().getWorkbook();
        if (isRedStyle(row.getRowStyle(), workbook)) {
            return true;
        }
        short last = row.getLastCellNum();
        for (int index = 0; index < last; index++) {
            Cell cell = row.getCell(index);
            if (cell != null && isRedCell(cell)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRedCell(Cell cell) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = cell.getCellStyle();
        if (isRedStyle(style, workbook)) {
            return true;
        }
        Font font = workbook.getFontAt(style.getFontIndex());
        if (font instanceof XSSFFont xssfFont) {
            if (isRedColor(xssfFont.getXSSFColor())) {
                return true;
            }
        } else if (font instanceof HSSFFont hssfFont && workbook instanceof HSSFWorkbook hssfWorkbook) {
            if (isRedColor(hssfFont.getHSSFColor(hssfWorkbook))) {
                return true;
            }
        }
        return isRedIndexed(font.getColor());
    }

    private static boolean isRedStyle(CellStyle style, Workbook workbook) {
        if (style == null) {
            return false;
        }
        FillPatternType pattern = style.getFillPattern();
        if (pattern != null && pattern != FillPatternType.NO_FILL) {
            if (isRedColor(style.getFillForegroundColorColor()) || isRedIndexed(style.getFillForegroundColor())) {
                return true;
            }
        }
        Font font = workbook.getFontAt(style.getFontIndex());
        return font instanceof XSSFFont xssfFont && isRedColor(xssfFont.getXSSFColor());
    }

    private static boolean isRedIndexed(int index) {
        return index == IndexedColors.RED.getIndex()
                || index == IndexedColors.DARK_RED.getIndex();
    }

    private static boolean isRedColor(Color color) {
        int[] rgb = rgbOf(color);
        if (rgb == null) {
            return false;
        }
        return isRedRgb(rgb[0], rgb[1], rgb[2]);
    }

    private static int[] rgbOf(Color color) {
        if (color instanceof XSSFColor xssf) {
            byte[] rgb = xssf.getRGB();
            if (rgb == null) {
                rgb = xssf.getARGB();
            }
            rgb = applyTint(rgb, xssf.hasTint() ? xssf.getTint() : 0);
            if (rgb != null && rgb.length >= 3) {
                int start = rgb.length == 4 ? 1 : 0;
                return new int[] { rgb[start] & 0xFF, rgb[start + 1] & 0xFF, rgb[start + 2] & 0xFF };
            }
        }
        if (color instanceof HSSFColor hssf) {
            short[] triplet = hssf.getTriplet();
            if (triplet != null && triplet.length >= 3) {
                return new int[] { triplet[0], triplet[1], triplet[2] };
            }
        }
        return null;
    }

    private static byte[] applyTint(byte[] rgb, double tint) {
        if (rgb == null || tint == 0) {
            return rgb;
        }
        int start = rgb.length == 4 ? 1 : 0;
        if (rgb.length < start + 3) {
            return rgb;
        }
        byte[] out = new byte[3];
        for (int index = 0; index < 3; index++) {
            int channel = rgb[start + index] & 0xFF;
            if (tint < 0) {
                channel = (int) Math.round(channel * (1 + tint));
            } else {
                channel = (int) Math.round(channel * (1 - tint) + 255 * tint);
            }
            out[index] = (byte) Math.max(0, Math.min(255, channel));
        }
        return out;
    }

    static boolean isRedRgb(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (max < 80 || max == min) {
            return false;
        }
        float saturation = (max - min) / (float) max;
        if (saturation < 0.12f) {
            return false;
        }
        float hue;
        if (max == red) {
            hue = (green - blue) / (float) (max - min);
        } else if (max == green) {
            hue = 2f + (blue - red) / (float) (max - min);
        } else {
            hue = 4f + (red - green) / (float) (max - min);
        }
        hue *= 60f;
        if (hue < 0) {
            hue += 360f;
        }
        return hue <= 22f || hue >= 338f;
    }
}
