package uy.ccisj.api.socio.importacion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PadronExcelParserCoverageTest {

    // ---------- errores de estructura ----------

    @Test
    void rechazaExcelSinHojasSinEncabezadosOSinColumnaDeRazon() throws Exception {
        IllegalArgumentException sinHojas = assertThrows(IllegalArgumentException.class,
                () -> PadronExcelParser.parse(entrada(new XSSFWorkbook())));
        assertEquals("El Excel no tiene hojas", sinHojas.getMessage());

        XSSFWorkbook vacio = new XSSFWorkbook();
        vacio.createSheet();
        IllegalArgumentException sinEncabezado = assertThrows(IllegalArgumentException.class,
                () -> PadronExcelParser.parse(entrada(vacio)));
        assertEquals("El Excel no tiene encabezados", sinEncabezado.getMessage());

        XSSFWorkbook sinRazon = new XSSFWorkbook();
        sinRazon.createSheet().createRow(0).createCell(0).setCellValue("DIRECCION");
        IllegalArgumentException faltaRazon = assertThrows(IllegalArgumentException.class,
                () -> PadronExcelParser.parse(entrada(sinRazon)));
        assertTrue(faltaRazon.getMessage().startsWith("No se encontró la columna de razón social"));
    }

    @Test
    void envuelveErroresDeLecturaComoArgumentoInvalido() {
        IllegalArgumentException basura = assertThrows(IllegalArgumentException.class,
                () -> PadronExcelParser.parse(new ByteArrayInputStream("no es un excel".getBytes())));
        assertTrue(basura.getMessage().startsWith("No se pudo leer el Excel: "));

        // Un stream vacío dispara una IllegalArgumentException propia de POI que se re-lanza tal cual.
        assertThrows(IllegalArgumentException.class,
                () -> PadronExcelParser.parse(new ByteArrayInputStream(new byte[0])));
    }

    // ---------- encabezados y celdas ----------

    @Test
    void reconoceVariantesDeEncabezadoYOmiteFilasVacias() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        XSSFRow header = sheet.createRow(0);
        String[] titulos = {
                "Nombre", "", "Dirección postal", "Celular", "Teléfono fijo", "Localidad", "Rubro",
                "Correo electrónico", "RUT", "BPS", "Inicio", "Afiliación", "Razón social", "Observaciones"
        };
        for (int i = 0; i < titulos.length; i++) {
            header.createCell(i).setCellValue(titulos[i]);
        }
        // Fila 2 inexistente (null) y fila 3 con celdas en blanco: ambas se omiten.
        XSSFRow blanca = sheet.createRow(2);
        blanca.createCell(0).setCellValue("   ");
        blanca.createCell(3);
        XSSFRow row = sheet.createRow(3);
        row.createCell(0).setCellValue("Panadería La Espiga");
        row.createCell(2).setCellValue("18 de Julio 100");
        row.createCell(3).setCellValue(99123456);
        row.createCell(4).setCellValue("43429999");
        row.createCell(5).setCellValue("Libertad");
        row.createCell(6).setCellValue("Alimentos");
        row.createCell(7).setCellValue("espiga@demo.com");
        row.createCell(8).setCellValue("210000000012");
        row.createCell(9).setCellValue("123456789");
        // Fecha numérica con formato de fecha: se devuelve en ISO.
        XSSFCellStyle fecha = workbook.createCellStyle();
        fecha.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        row.createCell(10).setCellValue(LocalDateTime.of(2019, 5, 20, 0, 0));
        row.getCell(10).setCellStyle(fecha);
        row.createCell(11).setCellValue("2020");
        row.createCell(12).setCellValue("Ignorada por duplicada");

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(entrada(workbook));

        assertEquals(1, rows.size());
        PadronExcelParser.RawRow fila = rows.getFirst();
        assertEquals(4, fila.filaExcel());
        assertEquals("Panadería La Espiga", fila.razonSocial());
        assertEquals("18 de Julio 100", fila.direccion());
        assertEquals("99123456", fila.celular());
        assertEquals("43429999", fila.telefono());
        assertEquals("Libertad", fila.localidad());
        assertEquals("Alimentos", fila.giro());
        assertEquals("espiga@demo.com", fila.mail());
        assertEquals("210000000012", fila.rut());
        assertEquals("123456789", fila.bps());
        assertEquals("2019-05-20", fila.inicio());
        assertEquals("2020", fila.fechaAfiliacion());
        assertFalse(fila.inactivo());
    }

    @Test
    void columnasAusentesDevuelvenTextoVacio() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("Nombre o razón");
        header.createCell(1).setCellValue("Giro");
        header.createCell(2).setCellValue("Email");
        header.createCell(3).setCellValue("Telefono");
        sheet.createRow(1).createCell(0).setCellValue("Solo nombre");

        PadronExcelParser.RawRow fila = PadronExcelParser.parse(entrada(workbook)).getFirst();

        assertEquals("Solo nombre", fila.razonSocial());
        assertEquals("", fila.giro());
        assertEquals("", fila.mail());
        assertEquals("", fila.telefono());
        assertEquals("", fila.direccion());
        assertEquals("", fila.bps());
        assertEquals("", fila.fechaAfiliacion());
    }

    @Test
    void normalizeHeaderYParseDateCubrenCasosLimite() {
        assertEquals("", PadronExcelParser.normalizeHeader(null));
        assertEquals("n bps", PadronExcelParser.normalizeHeader("  N°   BPS "));
        assertNull(PadronExcelParser.parseDate(null));
        assertEquals(LocalDate.of(2018, 3, 5), PadronExcelParser.parseDate("5-3-2018"));
        assertEquals(LocalDate.of(2018, 3, 5), PadronExcelParser.parseDate("5/3/18"));
        assertNull(PadronExcelParser.parseDate("31/02/2018x"));
        assertNull(PadronExcelParser.parseDate("marzo"));
    }

    // ---------- detección de filas rojas (XSSF) ----------

    @Test
    void isInactiveRowConFilaNulaEsFalso() {
        assertFalse(PadronExcelParser.isInactiveRow(null));
    }

    @Test
    void detectaEstiloRojoDeFilaCompleta() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("Razon social");
        XSSFCellStyle rojo = workbook.createCellStyle();
        rojo.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        rojo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFRow row = sheet.createRow(1);
        row.createCell(0).setCellValue("Baja por fila");
        row.setRowStyle(rojo);

        assertTrue(PadronExcelParser.isInactiveRow(row));
        assertTrue(PadronExcelParser.parse(entrada(workbook)).getFirst().inactivo());
    }

    @Test
    void rellenoNoRojoYColoresDeTemaNoMarcanInactivo() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        XSSFRow row = sheet.createRow(0);

        XSSFCellStyle amarillo = workbook.createCellStyle();
        amarillo.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        amarillo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        row.createCell(0).setCellValue("amarillo");
        row.getCell(0).setCellStyle(amarillo);

        // Color de tema sin RGB explícito: no se puede determinar, no es rojo.
        XSSFColor tema = new XSSFColor(new DefaultIndexedColorMap());
        tema.setTheme(5);
        XSSFCellStyle conTema = workbook.createCellStyle();
        conTema.setFillForegroundColor(tema);
        conTema.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        row.createCell(1).setCellValue("tema");
        row.getCell(1).setCellStyle(conTema);

        // Fuente con color de tema.
        XSSFFont fuenteTema = workbook.createFont();
        XSSFColor temaFuente = new XSSFColor(new DefaultIndexedColorMap());
        temaFuente.setTheme(1);
        fuenteTema.setColor(temaFuente);
        XSSFCellStyle estiloFuente = workbook.createCellStyle();
        estiloFuente.setFont(fuenteTema);
        row.createCell(2).setCellValue("fuente");
        row.getCell(2).setCellStyle(estiloFuente);

        assertFalse(PadronExcelParser.isInactiveRow(row));
    }

    @Test
    void aplicaTintAColoresRgb() throws Exception {
        // Rojo oscurecido (tint negativo) sigue siendo rojo.
        assertTrue(filaConRelleno(colorConTint(new byte[] {(byte) 255, 0, 0}, -0.5)));
        // Rojo aclarado (tint positivo) se vuelve rosa: sigue siendo rojo.
        assertTrue(filaConRelleno(colorConTint(new byte[] {(byte) 255, 0, 0}, 0.5)));
        // Verde aclarado no es rojo.
        assertFalse(filaConRelleno(colorConTint(new byte[] {0, (byte) 128, 0}, 0.3)));
    }

    @Test
    void applyTintYRgbOfToleranArreglosAtipicos() throws Exception {
        Method applyTint = PadronExcelParser.class.getDeclaredMethod("applyTint", byte[].class, double.class);
        applyTint.setAccessible(true);
        assertNull(applyTint.invoke(null, null, 0.5));
        byte[] corto = {1, 2};
        assertSame(corto, applyTint.invoke(null, corto, 0.5));
        byte[] argb = {(byte) 255, (byte) 200, 0, 0};
        byte[] tinted = (byte[]) applyTint.invoke(null, argb, -0.5);
        assertEquals(3, tinted.length);
        assertEquals(100, tinted[0] & 0xFF);

        Method isRedColor = PadronExcelParser.class.getDeclaredMethod("isRedColor", org.apache.poi.ss.usermodel.Color.class);
        isRedColor.setAccessible(true);
        assertFalse((boolean) isRedColor.invoke(null, (Object) null));
    }

    @Test
    void rgbOfContemplaArgbYTripletesIncompletos() throws Exception {
        Method rgbOf = PadronExcelParser.class.getDeclaredMethod("rgbOf", org.apache.poi.ss.usermodel.Color.class);
        rgbOf.setAccessible(true);

        // Sin RGB pero con ARGB de 4 bytes: se salta el canal alfa.
        XSSFColor argb = mock(XSSFColor.class);
        when(argb.getRGB()).thenReturn(null);
        when(argb.getARGB()).thenReturn(new byte[] {(byte) 255, (byte) 200, 10, 20});
        when(argb.hasTint()).thenReturn(false);
        assertArrayEquals(new int[] {200, 10, 20}, (int[]) rgbOf.invoke(null, argb));

        // RGB incompleto: no se puede determinar.
        XSSFColor corto = mock(XSSFColor.class);
        when(corto.getRGB()).thenReturn(new byte[] {1, 2});
        assertNull(rgbOf.invoke(null, corto));

        HSSFColor sinTriplete = mock(HSSFColor.class);
        when(sinTriplete.getTriplet()).thenReturn(null);
        assertNull(rgbOf.invoke(null, sinTriplete));
        HSSFColor tripleteCorto = mock(HSSFColor.class);
        when(tripleteCorto.getTriplet()).thenReturn(new short[] {255});
        assertNull(rgbOf.invoke(null, tripleteCorto));
    }

    @Test
    void isRedRgbCubreTodosLosTramosDelMatiz() {
        assertFalse(PadronExcelParser.isRedRgb(70, 0, 0));      // muy oscuro
        assertFalse(PadronExcelParser.isRedRgb(200, 200, 200)); // gris
        assertFalse(PadronExcelParser.isRedRgb(255, 240, 240)); // casi blanco, poca saturación
        assertTrue(PadronExcelParser.isRedRgb(255, 0, 20));     // matiz negativo que da la vuelta
        assertFalse(PadronExcelParser.isRedRgb(0, 0, 255));     // azul
        assertFalse(PadronExcelParser.isRedRgb(0, 255, 0));     // verde
        assertTrue(PadronExcelParser.isRedRgb(255, 0, 80));     // magenta rojizo (>= 338)
        assertFalse(PadronExcelParser.isRedRgb(200, 0, 255));   // violeta, max azul
    }

    @Test
    void isRedCellEvaluaLaFuenteSegunSuImplementacion() {
        XSSFColor rojo = new XSSFColor(new byte[] {(byte) 255, 0, 0}, new DefaultIndexedColorMap());
        XSSFFont fuenteXssfNeutra = mock(XSSFFont.class);
        XSSFFont fuenteXssfRoja = mock(XSSFFont.class);
        when(fuenteXssfRoja.getXSSFColor()).thenReturn(rojo);

        // El libro devuelve una fuente distinta en la segunda consulta: sólo así se llega al
        // chequeo de fuente XSSF dentro de isRedCell (con POI real, isRedStyle ya lo resolvió antes).
        assertTrue(PadronExcelParser.isInactiveRow(filaSimulada(
                FillPatternType.NO_FILL, fuenteXssfNeutra, fuenteXssfRoja)));

        // Fuente HSSF en un libro que no es HSSF: se cae al índice de color.
        HSSFFont fuenteHssf = mock(HSSFFont.class);
        when(fuenteHssf.getColor()).thenReturn(IndexedColors.RED.getIndex());
        assertTrue(PadronExcelParser.isInactiveRow(filaSimulada(
                null, fuenteHssf, fuenteHssf)));

        // Fuente genérica (ni XSSF ni HSSF) con color no rojo.
        Font generica = mock(Font.class);
        when(generica.getColor()).thenReturn(IndexedColors.BLUE.getIndex());
        assertFalse(PadronExcelParser.isInactiveRow(filaSimulada(
                null, generica, generica)));
    }

    @Test
    void cellConFilaOIndiceNuloDevuelveVacio() throws Exception {
        Method cell = PadronExcelParser.class.getDeclaredMethod("cell", Row.class, Integer.class);
        cell.setAccessible(true);
        assertEquals("", cell.invoke(null, null, 0));
        assertEquals("", cell.invoke(null, mock(Row.class), null));
    }

    // ---------- detección de filas rojas (HSSF / .xls) ----------

    @Test
    void detectaRojoEnLibrosXls() throws Exception {
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet sheet = workbook.createSheet();
        HSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("RAZON SOCIAL");

        HSSFFont fuenteRoja = workbook.createFont();
        fuenteRoja.setColor(IndexedColors.RED.getIndex());
        HSSFCellStyle estiloFuente = workbook.createCellStyle();
        estiloFuente.setFont(fuenteRoja);
        HSSFRow porFuente = sheet.createRow(1);
        porFuente.createCell(0).setCellValue("Fuente roja");
        porFuente.getCell(0).setCellStyle(estiloFuente);

        HSSFCellStyle relleno = workbook.createCellStyle();
        relleno.setFillForegroundColor(IndexedColors.RED.getIndex());
        relleno.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        HSSFRow porRelleno = sheet.createRow(2);
        porRelleno.createCell(0).setCellValue("Relleno rojo");
        porRelleno.getCell(0).setCellStyle(relleno);

        HSSFRow normal = sheet.createRow(3);
        normal.createCell(0).setCellValue("Normal");

        List<PadronExcelParser.RawRow> rows = PadronExcelParser.parse(entrada(workbook));
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).inactivo());
        assertTrue(rows.get(1).inactivo());
        assertFalse(rows.get(2).inactivo());
    }

    @Test
    void usaIndiceDeColorCuandoLaPaletaXlsFueRedefinida() throws Exception {
        HSSFWorkbook workbook = new HSSFWorkbook();
        // La paleta personalizada pinta de azul los índices "rojos": el RGB no es rojo pero el índice sí.
        workbook.getCustomPalette().setColorAtIndex(IndexedColors.RED.getIndex(), (byte) 0, (byte) 0, (byte) 255);
        workbook.getCustomPalette().setColorAtIndex(IndexedColors.DARK_RED.getIndex(), (byte) 0, (byte) 0, (byte) 200);
        HSSFSheet sheet = workbook.createSheet();

        HSSFFont fuente = workbook.createFont();
        fuente.setColor(IndexedColors.RED.getIndex());
        HSSFCellStyle estiloFuente = workbook.createCellStyle();
        estiloFuente.setFont(fuente);
        HSSFRow porFuente = sheet.createRow(0);
        porFuente.createCell(0).setCellValue("fuente");
        porFuente.getCell(0).setCellStyle(estiloFuente);

        HSSFCellStyle relleno = workbook.createCellStyle();
        relleno.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        relleno.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        HSSFRow porRelleno = sheet.createRow(1);
        porRelleno.createCell(0).setCellValue("relleno");
        porRelleno.getCell(0).setCellStyle(relleno);

        HSSFFont azul = workbook.createFont();
        azul.setColor(IndexedColors.BLUE.getIndex());
        HSSFCellStyle estiloAzul = workbook.createCellStyle();
        estiloAzul.setFont(azul);
        HSSFRow sinRojo = sheet.createRow(2);
        sinRojo.createCell(0).setCellValue("azul");
        sinRojo.getCell(0).setCellStyle(estiloAzul);

        assertTrue(PadronExcelParser.isInactiveRow(porFuente));
        assertTrue(PadronExcelParser.isInactiveRow(porRelleno));
        assertFalse(PadronExcelParser.isInactiveRow(sinRojo));
    }

    // ---------- utilidades ----------

    private static Row filaSimulada(FillPatternType patron, Font primera, Font segunda) {
        Workbook workbook = mock(Workbook.class);
        Sheet sheet = mock(Sheet.class);
        when(sheet.getWorkbook()).thenReturn(workbook);
        CellStyle style = mock(CellStyle.class);
        when(style.getFillPattern()).thenReturn(patron);
        when(style.getFontIndex()).thenReturn(0);
        when(workbook.getFontAt(0)).thenReturn(primera, segunda);
        Cell celda = mock(Cell.class);
        when(celda.getSheet()).thenReturn(sheet);
        when(celda.getCellStyle()).thenReturn(style);
        Row row = mock(Row.class);
        when(row.getSheet()).thenReturn(sheet);
        when(row.getRowStyle()).thenReturn(null);
        when(row.getLastCellNum()).thenReturn((short) 1);
        when(row.getCell(0)).thenReturn(celda);
        return row;
    }

    private static XSSFColor colorConTint(byte[] rgb, double tint) {
        XSSFColor color = new XSSFColor(rgb, new DefaultIndexedColorMap());
        color.setTint(tint);
        return color;
    }

    private static boolean filaConRelleno(XSSFColor color) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFRow row = workbook.createSheet().createRow(0);
        row.createCell(0).setCellValue("x");
        row.getCell(0).setCellStyle(style);
        return PadronExcelParser.isInactiveRow(row);
    }

    private static ByteArrayInputStream entrada(Workbook workbook) throws Exception {
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }
}
