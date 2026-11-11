package uy.ccisj.api.socio.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PadronExcelParserTest {

    @Test
    void normalizaEncabezadosConAcentosYSimbolos() {
        assertEquals("n de bps", PadronExcelParser.normalizeHeader("Nº de BPS"));
        assertEquals("fecha de afiliacion", PadronExcelParser.normalizeHeader("FECHA DE AFILIACIÓN"));
        assertEquals("nombre o razon social", PadronExcelParser.normalizeHeader("NOMBRE O RAZON SOCIAL"));
    }

    @Test
    void leeFilasMapeandoColumnasDelPadron() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            String[] titles = {
                    "NOMBRE O RAZON SOCIAL", "DIRECCION", "CELULAR", "TELEFONO", "LOCALIDAD",
                    "GIRO", "MAIL", "RUT", "Nº de BPS", "INICIO", "MES", "NUMERO", "FECHA DE AFILIACIÓN"
            };
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Los Membrillos SAS");
            row.createCell(1).setCellValue("Artigas 532");
            row.createCell(2).setCellValue("099111222");
            row.createCell(4).setCellValue("San José de Mayo");
            row.createCell(5).setCellValue("Comercio");
            row.createCell(6).setCellValue("contacto@demo.com");
            row.createCell(7).setCellValue("210000000012");
            row.createCell(8).setCellValue("123456789");
            row.createCell(9).setCellValue("15/03/2018");
            row.createCell(12).setCellValue("01/01/2010");
            workbook.write(output);
            excel = output.toByteArray();
        }

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(new ByteArrayInputStream(excel));
        assertEquals(1, rows.size());
        PadronExcelParser.RawRow first = rows.getFirst();
        assertEquals("Los Membrillos SAS", first.razonSocial());
        assertEquals("Artigas 532", first.direccion());
        assertEquals("099111222", first.celular());
        assertEquals("San José de Mayo", first.localidad());
        assertEquals("Comercio", first.giro());
        assertEquals("123456789", first.bps());
        assertEquals("15/03/2018", first.inicio());
        assertEquals("01/01/2010", first.fechaAfiliacion());
        assertFalse(first.inactivo());
    }

    @Test
    void marcaFilaRojaComoInactiva() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("NOMBRE O RAZON SOCIAL");
            XSSFCellStyle red = workbook.createCellStyle();
            red.setFillForegroundColor(IndexedColors.RED.getIndex());
            red.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFRow inactive = sheet.createRow(1);
            inactive.createCell(0).setCellValue("Socio dado de baja");
            inactive.getCell(0).setCellStyle(red);
            XSSFRow active = sheet.createRow(2);
            active.createCell(0).setCellValue("Socio activo");
            workbook.write(output);
            excel = output.toByteArray();
        }

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(new ByteArrayInputStream(excel));
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).inactivo());
        assertFalse(rows.get(1).inactivo());
    }

    @Test
    void marcaFuenteRojaComoInactiva() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("NOMBRE O RAZON SOCIAL");
            XSSFFont font = workbook.createFont();
            font.setColor(IndexedColors.RED.getIndex());
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font);
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Baja por fuente roja");
            row.getCell(0).setCellStyle(style);
            workbook.write(output);
            excel = output.toByteArray();
        }

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(new ByteArrayInputStream(excel));
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().inactivo());
    }

    @Test
    void reconoceRojoClaroTipicoDeExcel() {
        assertTrue(PadronExcelParser.isRedRgb(255, 0, 0));
        assertTrue(PadronExcelParser.isRedRgb(255, 199, 206));
        assertTrue(PadronExcelParser.isRedRgb(156, 0, 6));
        assertFalse(PadronExcelParser.isRedRgb(255, 255, 0));
        assertFalse(PadronExcelParser.isRedRgb(255, 165, 0));
        assertFalse(PadronExcelParser.isRedRgb(0, 128, 0));
    }

    @Test
    void reconoceRellenoRosaDeEstiloMaloDeExcel() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("NOMBRE O RAZON SOCIAL");
            XSSFCellStyle bad = workbook.createCellStyle();
            bad.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 255, (byte) 199, (byte) 206}, new DefaultIndexedColorMap()));
            bad.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Estilo Bad");
            row.getCell(0).setCellStyle(bad);
            workbook.write(output);
            excel = output.toByteArray();
        }

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(new ByteArrayInputStream(excel));
        assertTrue(rows.getFirst().inactivo());
    }

    @Test
    void parseDateAceptaFormatosComunes() {
        assertEquals(LocalDate.of(2018, 3, 15), PadronExcelParser.parseDate("15/03/2018"));
        assertEquals(LocalDate.of(2010, 1, 1), PadronExcelParser.parseDate("2010-01-01"));
        assertEquals(LocalDate.of(2015, 1, 1), PadronExcelParser.parseDate("2015"));
        assertTrue(PadronExcelParser.parseDate(" ") == null);
    }
}
