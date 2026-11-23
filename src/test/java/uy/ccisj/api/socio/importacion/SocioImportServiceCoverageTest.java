package uy.ccisj.api.socio.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.Direccion;
import uy.ccisj.api.socio.DireccionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.socio.SocioSubscriptionHistory;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewFila;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewResponse;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class SocioImportServiceCoverageTest {
    // Orden de columnas del padrón usado en todas las planillas de prueba.
    private static final String[] TITULOS = {
            "NOMBRE O RAZON SOCIAL", "DIRECCION", "CELULAR", "TELEFONO", "LOCALIDAD",
            "GIRO", "MAIL", "RUT", "Nº de BPS", "INICIO", "FECHA DE AFILIACIÓN"
    };
    private static final LocalDate HOY = LocalDate.of(2026, 11, 22);

    private final SocioRepository socios = mock(SocioRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DireccionRepository direcciones = mock(DireccionRepository.class);
    private final RubroEmpleoRepository rubros = mock(RubroEmpleoRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SocioSubscriptionHistory historial = mock(SocioSubscriptionHistory.class);
    private SocioImportService service;

    @BeforeEach
    void setUp() {
        service = new SocioImportService(socios, users, direcciones, rubros, encoder, historial);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(direcciones.save(any(Direccion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(socios.save(any(Socio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historial.today()).thenReturn(HOY);
    }

    // ---------- validación del archivo ----------

    @Test
    void rechazaArchivoNuloVacioOConExtensionInvalida() throws Exception {
        assertStatus("Seleccioná un archivo Excel", () -> service.preview(null));
        assertStatus("Seleccioná un archivo Excel",
                () -> service.preview(new MockMultipartFile("file", "padron.xlsx", null, new byte[0])));

        MultipartFile sinNombre = mock(MultipartFile.class);
        when(sinNombre.isEmpty()).thenReturn(false);
        when(sinNombre.getOriginalFilename()).thenReturn(null);
        assertStatus("El archivo debe ser .xlsx o .xls", () -> service.preview(sinNombre));

        assertStatus("El archivo debe ser .xlsx o .xls",
                () -> service.importar(new MockMultipartFile("file", "padron.csv", null, new byte[] {1, 2})));
        verify(users, never()).save(any());
    }

    @Test
    void informaErrorGenericoSiNoSePuedeAbrirElArchivo() throws Exception {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getOriginalFilename()).thenReturn("PADRON.XLS");
        when(roto.getInputStream()).thenThrow(new IOException("disco"));

        assertStatus("No se pudo leer el archivo Excel", () -> service.preview(roto));
    }

    @Test
    void propagaComoBadRequestLosErroresDelParser() throws Exception {
        ResponseStatusException invalido = assertThrows(ResponseStatusException.class,
                () -> service.preview(archivo(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})));
        assertEquals(HttpStatus.BAD_REQUEST, invalido.getStatusCode());
        assertTrue(invalido.getReason().startsWith("No se pudo leer el Excel"));

        byte[] sinRazon;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("DIRECCION");
            workbook.write(output);
            sinRazon = output.toByteArray();
        }
        ResponseStatusException faltaColumna = assertThrows(ResponseStatusException.class,
                () -> service.preview(archivo(sinRazon)));
        assertTrue(faltaColumna.getReason().startsWith("No se encontró la columna de razón social"));
    }

    @Test
    void rechazaExcelSinFilasDeSocios() throws Exception {
        assertStatus("El Excel no tiene filas de socios para importar", () -> service.preview(excel()));
    }

    // ---------- preview: mapeo de filas ----------

    @Test
    void previewDeFilaCompletaNoMarcaCamposFicticios() throws Exception {
        RubroEmpleo comercio = rubro("Comercio", 3L);
        when(rubros.findAll()).thenReturn(List.of(comercio));

        PreviewResponse response = service.preview(excel(fila(
                "  Los Membrillos SAS ", "Artigas 532", "099111222", "43420000", "san jose de mayo",
                "COMERCIO", "Contacto@Demo.COM", "21.000000.0012", "123.456.789", "15/03/2018", "01/01/2010")));

        PreviewFila fila = response.filas().getFirst();
        assertEquals(2, fila.filaExcel());
        assertEquals("IMPORTABLE", fila.estado());
        assertNull(fila.motivo());
        assertEquals("Los Membrillos SAS", fila.razonSocial());
        assertEquals("123456789", fila.bps());
        assertEquals("210000000012", fila.rut());
        assertEquals("099111222", fila.telefono());
        assertEquals("contacto@demo.com", fila.email());
        assertEquals("Artigas", fila.calle());
        assertEquals("532", fila.numero());
        assertEquals("San José de Mayo", fila.localidad());
        assertEquals("Comercio", fila.rubro());
        assertEquals("2010-01-01", fila.fechaAlta());
        assertEquals("2018-03-15", fila.fechaAniversario());
        assertTrue(fila.camposFicticios().isEmpty());
        assertFalse(fila.inactivo());

        var resumen = response.resumen();
        assertEquals(1, resumen.total());
        assertEquals(1, resumen.importables());
        assertEquals(0, resumen.conFicticios());
        assertEquals(0, resumen.duplicados());
        assertEquals(0, resumen.errores());
        assertEquals(0, resumen.inactivos());
        assertEquals(0, resumen.creados());
    }

    @Test
    void previewCompletaDatosProvisionalesCuandoFaltanTodos() throws Exception {
        when(rubros.findAll()).thenReturn(List.of(rubro("Comercio", 3L), rubro("Sin Clasificar", 7L)));

        PreviewResponse response = service.preview(excel(fila(
                "Solo Razon", "", "", "", "", "", "", "", "", "", "")));

        PreviewFila fila = response.filas().getFirst();
        assertEquals("IMPORTABLE", fila.estado());
        assertEquals("Se completarán datos provisionales en los campos marcados", fila.motivo());
        assertEquals("990000000001", fila.bps());
        assertEquals("980000000002", fila.rut());
        assertEquals(SocioImportService.TELEFONO_PENDIENTE, fila.telefono());
        assertEquals("import.3@incompleto.ccisj.local", fila.email());
        assertEquals(SocioImportService.CALLE_PENDIENTE, fila.calle());
        assertNull(fila.numero());
        assertEquals("Otra localidad", fila.localidad());
        assertEquals("Sin Clasificar", fila.rubro());
        assertEquals(LocalDate.now().toString(), fila.fechaAlta());
        assertEquals("2000-01-01", fila.fechaAniversario());
        assertEquals(List.of(
                CamposFicticios.BPS, CamposFicticios.RUT, CamposFicticios.TELEFONO,
                CamposFicticios.EMAIL_CONTACTO, CamposFicticios.EMAIL_USUARIO, CamposFicticios.CALLE,
                CamposFicticios.LOCALIDAD, CamposFicticios.RUBRO, CamposFicticios.FECHA_ALTA,
                CamposFicticios.FECHA_ANIVERSARIO), fila.camposFicticios());
        assertEquals(1, response.resumen().conFicticios());
    }

    @Test
    void previewResuelveVariantesDeTelefonoDireccionLocalidadRubroYCorreo() throws Exception {
        // Sin "Sin clasificar" en el catálogo: el rubro queda sin id.
        when(rubros.findAll()).thenReturn(List.of(rubro("Comercio", 3L)));
        String direccionLarga = "Calle " + "x".repeat(200);

        PreviewResponse response = service.preview(excel(
                fila("Uno", "Sin numero", "", "43421111", "Otra localidad", "Industria", "sin-arroba",
                        "12345678", "111111111", "2015", "2016"),
                fila("Dos", "Ruta 1 km 30", "", "", "Montevideo", "", "con espacio@demo.com",
                        "123", "222222222", "texto", "texto"),
                fila("Tres", "Calle 1234567890123456789012", "098", "", "", "", "",
                        "", "333333333", "", ""),
                fila("Cuatro", direccionLarga, "098", "", "Kiyú-Ordeig", "", "c@d.com",
                        "", "444444444", "", "")));

        List<PreviewFila> filas = response.filas();

        PreviewFila uno = filas.get(0);
        assertEquals("43421111", uno.telefono());
        assertEquals("Sin numero", uno.calle());
        assertNull(uno.numero());
        assertEquals("Otra localidad", uno.localidad());
        assertEquals("12345678", uno.rut());
        assertEquals(SocioImportService.RUBRO_SIN_CLASIFICAR, uno.rubro());
        assertEquals("2016-01-01", uno.fechaAlta());
        assertEquals("2015-01-01", uno.fechaAniversario());
        // Localidad explícita "Otra localidad" no es dato ficticio; correo inválido sí.
        assertEquals(List.of(CamposFicticios.EMAIL_CONTACTO, CamposFicticios.EMAIL_USUARIO, CamposFicticios.RUBRO),
                uno.camposFicticios());

        PreviewFila dos = filas.get(1);
        assertEquals("Ruta 1 km", dos.calle());
        assertEquals("30", dos.numero());
        assertEquals("Otra localidad", dos.localidad());
        assertTrue(dos.camposFicticios().contains(CamposFicticios.LOCALIDAD));
        assertTrue(dos.camposFicticios().contains(CamposFicticios.TELEFONO));
        assertTrue(dos.camposFicticios().contains(CamposFicticios.RUT));
        assertTrue(dos.camposFicticios().contains(CamposFicticios.EMAIL_USUARIO));

        PreviewFila tres = filas.get(2);
        // Número de puerta de más de 20 caracteres: se guarda todo como calle.
        assertEquals("Calle 1234567890123456789012", tres.calle());
        assertNull(tres.numero());
        assertEquals("098", tres.telefono());

        PreviewFila cuatro = filas.get(3);
        assertEquals(160, cuatro.calle().length());
        assertEquals("Kiyú-Ordeig", cuatro.localidad());
        assertEquals("c@d.com", cuatro.email());
        assertFalse(cuatro.camposFicticios().contains(CamposFicticios.LOCALIDAD));
    }

    @Test
    void previewDetectaDuplicadosYErrores() throws Exception {
        when(rubros.findAll()).thenReturn(List.of());
        when(users.existsByBps("100000001")).thenReturn(true);
        when(users.existsByCedula("100000002")).thenReturn(true);
        when(socios.existsByRutIgnoreCase("210000000099")).thenReturn(true);
        when(users.existsByEmailIgnoreCase("ya@existe.com")).thenReturn(true);

        PreviewResponse response = service.preview(excel(
                fila("", "Artigas 1", "", "", "", "", "", "", "", "", ""),
                fila("Bps existente", "", "", "", "", "", "", "", "100000001", "", ""),
                fila("Bps cedula", "", "", "", "", "", "", "", "100000002", "", ""),
                fila("Primero", "", "", "", "", "", "uno@demo.com", "210000000001", "100000003", "", ""),
                fila("Bps repetido", "", "", "", "", "", "", "", "100000003", "", ""),
                fila("Rut existente", "", "", "", "", "", "", "210000000099", "100000004", "", ""),
                fila("Rut repetido", "", "", "", "", "", "", "210000000001", "100000005", "", ""),
                fila("Mail existente", "", "", "", "", "", "Ya@Existe.com", "", "100000006", "", ""),
                fila("Mail repetido", "", "", "", "", "", "UNO@demo.com", "", "100000007", "", "")));

        List<PreviewFila> filas = response.filas();
        // La fila 1 tiene dirección pero la razón social en blanco: se reporta error.
        assertEquals("ERROR", filas.get(0).estado());
        assertEquals("Falta la razón social", filas.get(0).motivo());
        assertNull(filas.get(0).razonSocial());
        assertTrue(filas.get(0).camposFicticios().isEmpty());

        String motivoBps = "Ya existe un socio con ese BPS (o está repetido en el Excel)";
        String motivoRut = "Ya existe un socio con ese RUT (o está repetido en el Excel)";
        String motivoMail = "Ya existe una cuenta con ese correo (o está repetido en el Excel)";
        assertDuplicado(filas.get(1), motivoBps);
        assertEquals("100000001", filas.get(1).bps());
        assertDuplicado(filas.get(2), motivoBps);
        assertEquals("IMPORTABLE", filas.get(3).estado());
        assertDuplicado(filas.get(4), motivoBps);
        assertDuplicado(filas.get(5), motivoRut);
        assertEquals("210000000099", filas.get(5).rut());
        assertDuplicado(filas.get(6), motivoRut);
        assertDuplicado(filas.get(7), motivoMail);
        assertEquals("ya@existe.com", filas.get(7).email());
        assertDuplicado(filas.get(8), motivoMail);

        var resumen = response.resumen();
        assertEquals(9, resumen.total());
        assertEquals(1, resumen.importables());
        assertEquals(7, resumen.duplicados());
        assertEquals(1, resumen.errores());
    }

    @Test
    void generadoresFicticiosEvitanValoresYaUsadosEnBaseOEnElExcel() throws Exception {
        when(rubros.findAll()).thenReturn(List.of());
        when(users.existsByBps("990000000001")).thenReturn(true);
        when(users.existsByCedula("990000000002")).thenReturn(true);
        when(socios.existsByRutIgnoreCase("980000000005")).thenReturn(true);
        when(users.existsByEmailIgnoreCase("import.8@incompleto.ccisj.local")).thenReturn(true);

        PreviewResponse response = service.preview(excel(
                // Fila real que ya "ocupa" valores que generaría el contador.
                fila("Real", "", "", "", "", "", "import.9@incompleto.ccisj.local", "980000000006",
                        "990000000003", "", ""),
                fila("Ficticia", "", "", "", "", "", "", "", "", "", "")));

        PreviewFila ficticia = response.filas().get(1);
        assertEquals("IMPORTABLE", ficticia.estado());
        assertEquals("990000000004", ficticia.bps());
        assertEquals("980000000007", ficticia.rut());
        assertEquals("import.10@incompleto.ccisj.local", ficticia.email());
    }

    @Test
    void previewMarcaFilasRojasComoInactivas() throws Exception {
        when(rubros.findAll()).thenReturn(List.of(rubro("Comercio", 3L)));

        PreviewResponse response = service.preview(excelConInactivas(
                List.of(true, true, false),
                fila("Baja incompleta", "", "", "", "", "", "", "", "", "", ""),
                fila("Baja completa", "Artigas 1", "099", "", "Libertad", "Comercio", "baja@demo.com",
                        "210000000001", "123456789", "01/01/2010", "01/01/2011"),
                fila("Activa", "Artigas 2", "099", "", "Libertad", "Comercio", "activa@demo.com",
                        "210000000002", "123456780", "01/01/2010", "01/01/2011")));

        PreviewFila incompleta = response.filas().get(0);
        assertEquals("INACTIVO", incompleta.estado());
        assertTrue(incompleta.inactivo());
        assertEquals("Celda en rojo en el Excel: se importará como socio inactivo. "
                + "Se completarán datos provisionales en los campos marcados", incompleta.motivo());

        PreviewFila completa = response.filas().get(1);
        assertEquals("INACTIVO", completa.estado());
        assertEquals("Celda en rojo en el Excel: se importará como socio inactivo", completa.motivo());

        assertEquals("IMPORTABLE", response.filas().get(2).estado());
        assertEquals(3, response.resumen().importables());
        assertEquals(2, response.resumen().inactivos());
        assertEquals(1, response.resumen().conFicticios());
    }

    // ---------- importar ----------

    @Test
    void importarPersisteSociosActivosEInactivosYOmiteDuplicados() throws Exception {
        RubroEmpleo sinClasificar = rubro("Sin clasificar", 7L);
        RubroEmpleo comercio = rubro("Comercio", 3L);
        when(rubros.findAll()).thenReturn(List.of(comercio, sinClasificar));
        when(rubros.findByNombreRubroIgnoreCase("Sin clasificar")).thenReturn(Optional.of(sinClasificar));
        when(rubros.findById(3L)).thenReturn(Optional.of(comercio));
        when(rubros.findById(7L)).thenReturn(Optional.empty());
        when(users.existsByBps("555555555")).thenReturn(true);

        ImportResponse response = service.importar(excelConInactivas(
                List.of(false, false, false, true),
                fila("Activa SA", "Artigas 532", "099111222", "", "Libertad", "Comercio", "activa@demo.com",
                        "210000000001", "123456789", "15/03/2018", "01/01/2010"),
                fila("Sin rubro", "", "", "", "", "Otro giro", "", "", "", "", ""),
                fila("Duplicada", "", "", "", "", "", "", "", "555555555", "", ""),
                fila("Baja futura", "", "", "", "", "Comercio", "", "", "", "", "01/01/2099")));

        assertEquals(3, response.resumen().creados());
        assertEquals(4, response.resumen().total());
        assertEquals(1, response.resumen().duplicados());
        assertEquals(1, response.resumen().inactivos());
        assertEquals(1, response.omitidas().size());
        assertEquals("DUPLICADO", response.omitidas().getFirst().estado());
        assertEquals("Duplicada", response.omitidas().getFirst().razonSocial());

        ArgumentCaptor<User> usuarios = ArgumentCaptor.forClass(User.class);
        verify(users, times(3)).save(usuarios.capture());
        User activo = usuarios.getAllValues().get(0);
        assertEquals("activa@demo.com", activo.getEmail());
        assertEquals("123456789", activo.getBps());
        assertEquals("hash", activo.getPasswordHash());
        assertEquals(Role.SOCIO, activo.getRole());
        assertNull(activo.getCedula());
        assertTrue(activo.isActivo());
        assertNull(activo.getFechaBaja());
        User inactivo = usuarios.getAllValues().get(2);
        assertFalse(inactivo.isActivo());
        assertNotNull(inactivo.getFechaBaja());

        ArgumentCaptor<Socio> guardados = ArgumentCaptor.forClass(Socio.class);
        verify(socios, times(3)).save(guardados.capture());
        Socio socioActivo = guardados.getAllValues().get(0);
        assertSame(activo, socioActivo.getUsuario());
        assertEquals("Activa SA", socioActivo.getRazonSocial());
        assertEquals("210000000001", socioActivo.getRut());
        assertEquals("099111222", socioActivo.getTelefono());
        assertEquals("activa@demo.com", socioActivo.getEmailContacto());
        assertEquals("Artigas", socioActivo.getDireccion().getCalle());
        assertEquals("532", socioActivo.getDireccion().getNumero());
        assertEquals("Libertad", socioActivo.getDireccion().getLocalidad());
        assertSame(comercio, socioActivo.getRubro());
        assertFalse(socioActivo.isEsDirectivo());
        assertEquals(LocalDate.of(2010, 1, 1), socioActivo.getFechaAlta());
        assertEquals(LocalDate.of(2018, 3, 15), socioActivo.getFechaAniversario());
        assertEquals(EstadoMorosidad.AL_DIA, socioActivo.getEstadoMorosidad());
        assertTrue(socioActivo.isImportadoMasivo());
        assertFalse(socioActivo.isAccesoHabilitado());
        assertNull(socioActivo.getCamposFicticios());

        // Rubro "Sin clasificar" con id que ya no existe: se usa el obtenido al inicio.
        Socio sinRubro = guardados.getAllValues().get(1);
        assertSame(sinClasificar, sinRubro.getRubro());
        assertTrue(CamposFicticios.tiene(sinRubro.getCamposFicticios(), CamposFicticios.RUBRO));

        Socio baja = guardados.getAllValues().get(2);
        assertEquals(EstadoMorosidad.INACTIVO, baja.getEstadoMorosidad());
        verify(historial).record(null, "ALTA", LocalDate.of(2099, 1, 1));
        // La baja no puede quedar antes del alta: se usa la fecha de alta futura.
        verify(historial).record(null, "BAJA", LocalDate.of(2099, 1, 1));
        verify(historial).record(null, "ALTA", LocalDate.of(2010, 1, 1));
        verify(rubros, never()).save(any());
    }

    @Test
    void importarCreaRubroSinClasificarYReportaFallosAlGuardar() throws Exception {
        when(rubros.findAll()).thenReturn(List.of());
        when(rubros.findByNombreRubroIgnoreCase(SocioImportService.RUBRO_SIN_CLASIFICAR)).thenReturn(Optional.empty());
        RubroEmpleo creado = rubro(SocioImportService.RUBRO_SIN_CLASIFICAR, 9L);
        when(rubros.save(any(RubroEmpleo.class))).thenReturn(creado);
        List<String> razonesGuardadas = new ArrayList<>();
        when(socios.save(any(Socio.class))).thenAnswer(invocation -> {
            Socio socio = invocation.getArgument(0);
            if ("Falla".equals(socio.getRazonSocial())) {
                throw new IllegalStateException("restricción violada");
            }
            razonesGuardadas.add(socio.getRazonSocial());
            if ("Baja sin alta".equals(socio.getRazonSocial())) {
                // Simula una entidad devuelta sin fecha de alta.
                Socio sinAlta = new Socio(socio.getUsuario(), socio.getRazonSocial());
                sinAlta.setFechaAlta(null);
                return sinAlta;
            }
            return socio;
        });

        ImportResponse response = service.importar(excelConInactivas(
                List.of(false, true, true),
                fila("Falla", "", "", "", "", "", "", "", "", "", ""),
                fila("Baja pasada", "", "", "", "", "", "", "", "", "", "01/01/2010"),
                fila("Baja sin alta", "", "", "", "", "", "", "", "", "", "")));

        assertEquals(2, response.resumen().creados());
        // La fila que falla al guardar deja de contar como importable y suma como error.
        assertEquals(2, response.resumen().importables());
        assertEquals(1, response.resumen().errores());
        assertEquals(3, response.resumen().total());
        assertEquals(1, response.omitidas().size());
        PreviewFila omitida = response.omitidas().getFirst();
        assertEquals("ERROR", omitida.estado());
        assertEquals("No se pudo guardar: restricción violada", omitida.motivo());
        assertEquals("Falla", omitida.razonSocial());
        assertEquals("990000000001", omitida.bps());
        assertEquals(List.of("Baja pasada", "Baja sin alta"), razonesGuardadas);

        ArgumentCaptor<RubroEmpleo> nuevoRubro = ArgumentCaptor.forClass(RubroEmpleo.class);
        verify(rubros).save(nuevoRubro.capture());
        assertEquals(SocioImportService.RUBRO_SIN_CLASIFICAR, nuevoRubro.getValue().getNombreRubro());
        verify(rubros, never()).findById(any());
        // Alta pasada y alta nula: la baja queda en la fecha de hoy.
        verify(historial, times(2)).record(null, "BAJA", HOY);
    }

    @Test
    void toleraCeldasNulasDevueltasPorElParser() throws Exception {
        when(rubros.findAll()).thenReturn(List.of());
        PadronExcelParser.RawRow sinRazon = new PadronExcelParser.RawRow(
                2, null, null, null, null, null, null, null, null, null, null, null, false);
        PadronExcelParser.RawRow soloRazon = new PadronExcelParser.RawRow(
                3, "Con nulos", null, null, null, null, null, null, null, null, null, null, false);
        try (MockedStatic<PadronExcelParser> parser = mockStatic(PadronExcelParser.class)) {
            parser.when(() -> PadronExcelParser.parse(any())).thenReturn(List.of(sinRazon, soloRazon));
            parser.when(() -> PadronExcelParser.parseDate(any())).thenCallRealMethod();

            PreviewResponse response = service.preview(archivo(new byte[] {1}));

            PreviewFila error = response.filas().get(0);
            assertEquals("ERROR", error.estado());
            assertNull(error.razonSocial());
            PreviewFila completada = response.filas().get(1);
            assertEquals("IMPORTABLE", completada.estado());
            assertEquals("990000000001", completada.bps());
            assertEquals(SocioImportService.TELEFONO_PENDIENTE, completada.telefono());
            assertEquals("Otra localidad", completada.localidad());
            assertTrue(completada.camposFicticios().contains(CamposFicticios.LOCALIDAD));
        }
    }

    // ---------- utilidades ----------

    private static RubroEmpleo rubro(String nombre, Long id) {
        RubroEmpleo rubro = new RubroEmpleo(nombre);
        ReflectionTestUtils.setField(rubro, "id", id);
        return rubro;
    }

    private static void assertDuplicado(PreviewFila fila, String motivo) {
        assertEquals("DUPLICADO", fila.estado());
        assertEquals(motivo, fila.motivo());
        assertTrue(fila.camposFicticios().isEmpty());
        assertNull(fila.telefono());
    }

    private static void assertStatus(String reason, Executable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }

    private static String[] fila(String... valores) {
        return valores;
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("file", "padron.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
    }

    private static MockMultipartFile excel(String[]... filas) throws IOException {
        List<Boolean> inactivas = new ArrayList<>();
        for (int i = 0; i < filas.length; i++) {
            inactivas.add(false);
        }
        return excelConInactivas(inactivas, filas);
    }

    private static MockMultipartFile excelConInactivas(List<Boolean> inactivas, String[]... filas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet();
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < TITULOS.length; i++) {
                header.createCell(i).setCellValue(TITULOS[i]);
            }
            XSSFCellStyle rojo = workbook.createCellStyle();
            rojo.setFillForegroundColor(IndexedColors.RED.getIndex());
            rojo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int r = 0; r < filas.length; r++) {
                XSSFRow row = sheet.createRow(r + 1);
                String[] valores = filas[r];
                for (int c = 0; c < valores.length; c++) {
                    // Una fila completamente vacía se omitiría: se fuerza un espacio en la primera celda.
                    String valor = c == 0 && valores[c].isEmpty() ? " " : valores[c];
                    if (!valor.isEmpty()) {
                        row.createCell(c).setCellValue(valor);
                    }
                }
                if (inactivas.get(r)) {
                    row.getCell(0).setCellStyle(rojo);
                }
            }
            workbook.write(output);
            return archivo(output.toByteArray());
        }
    }
}
