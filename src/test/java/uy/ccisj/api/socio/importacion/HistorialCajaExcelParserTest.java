package uy.ccisj.api.socio.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HistorialCajaExcelParserTest {
    @Test
    void parseaLasColumnasObligatoriasYLasOpcionalesDelExcel() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Cuotas pagadas");
            var header = sheet.createRow(0);
            List.of("Periodo", "Monto cuota", "Fecha vencimiento", "Fecha pago", "Monto pagado",
                    "Metodo de pago", "Referencia", "Observaciones")
                    .forEach(value -> header.createCell(header.getPhysicalNumberOfCells()).setCellValue(value));
            var data = sheet.createRow(1);
            List.of("2024-03", "1250,50", "30/04/2024", "15/04/2024", "1250,50",
                    "TRANSFERENCIA", "TR-18", "Pago histórico")
                    .forEach(value -> data.createCell(data.getPhysicalNumberOfCells()).setCellValue(value));
            workbook.write(output);
            file = output.toByteArray();
        }

        List<HistorialCajaExcelParser.RawRow> rows = HistorialCajaExcelParser.parse(new ByteArrayInputStream(file));

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).filaExcel());
        assertEquals("2024-03", rows.get(0).periodo());
        assertEquals("1250,50", rows.get(0).montoCuota());
        assertEquals("30/04/2024", rows.get(0).fechaVencimiento());
        assertEquals("15/04/2024", rows.get(0).fechaPago());
        assertEquals("TRANSFERENCIA", rows.get(0).metodoPago());
        assertEquals("TR-18", rows.get(0).referencia());
    }

    @Test
    void rechazaUnExcelSinLosEncabezadosObligatorios() throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Cuotas").createRow(0).createCell(0).setCellValue("Mes");
            workbook.write(output);
            file = output.toByteArray();
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> HistorialCajaExcelParser.parse(new ByteArrayInputStream(file)));
        assertEquals("Faltan columnas obligatorias: periodo, monto cuota, fecha vencimiento, fecha pago, "
                        + "monto pagado, metodo de pago.",
                error.getMessage());
    }
}
