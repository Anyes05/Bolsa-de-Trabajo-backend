package uy.ccisj.api.socio.importacion;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class HistorialCajaExcelParser {
    private static final int MAX_ROWS = 500;

    public record RawRow(
            int filaExcel,
            String periodo,
            String montoCuota,
            String fechaVencimiento,
            String fechaPago,
            String montoPagado,
            String metodoPago,
            String referencia,
            String observaciones) {}

    private HistorialCajaExcelParser() {}

    public static List<RawRow> parse(InputStream input) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("El Excel no contiene hojas.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new IllegalArgumentException("La primera hoja no tiene encabezados.");
            }
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-UY"));
            Map<String, Integer> columns = mapHeaders(header, formatter);
            List<String> required = List.of(
                    "periodo", "monto cuota", "fecha vencimiento", "fecha pago", "monto pagado", "metodo de pago");
            List<String> missing = required.stream().filter(column -> !columns.containsKey(column)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Faltan columnas obligatorias: " + String.join(", ", missing) + ".");
            }

            List<RawRow> rows = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isEmpty(row, formatter)) {
                    continue;
                }
                rows.add(new RawRow(
                        index + 1,
                        cell(row, columns.get("periodo"), formatter),
                        cell(row, columns.get("monto cuota"), formatter),
                        cell(row, columns.get("fecha vencimiento"), formatter),
                        cell(row, columns.get("fecha pago"), formatter),
                        cell(row, columns.get("monto pagado"), formatter),
                        cell(row, columns.get("metodo de pago"), formatter),
                        cell(row, columns.get("referencia"), formatter),
                        cell(row, columns.get("observaciones"), formatter)));
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("El Excel supera el máximo de " + MAX_ROWS + " cuotas por socio.");
                }
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("El Excel no tiene cuotas para importar.");
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No se pudo leer el archivo Excel.", exception);
        }
    }

    private static Map<String, Integer> mapHeaders(Row header, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String name = PadronExcelParser.normalizeHeader(cell(header, index, formatter));
            if (!name.isEmpty()) {
                columns.putIfAbsent(name, index);
            }
        }
        return columns;
    }

    private static String cell(Row row, Integer index, DataFormatter formatter) {
        if (index == null || row.getCell(index) == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static boolean isEmpty(Row row, DataFormatter formatter) {
        for (int index = 0; index < row.getLastCellNum(); index++) {
            if (!cell(row, index, formatter).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
