package uy.ccisj.api.postulante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoCivil;
import uy.ccisj.api.domain.Genero;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Carga de CV con mocks: resolución de postulante y perfil, validación de archivo, extracción de datos
 * opcionales desde el PDF y limpieza del archivo en S3 cuando la transacción no confirma.
 */
class CvServiceCoverageTest {
    private static final String EMAIL = "persona@example.test";
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String SQL_PERFIL_POR_ID = "SELECT id FROM perfiles_laborales WHERE id = ?";

    private final PostulanteRepository postulantes = mock(PostulanteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CvRepository cvs = mock(CvRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private User user;
    private Postulante postulante;
    private PerfilLaboral perfil;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        user = new User(EMAIL, null, "hash", Role.POSTULANTE);
        ReflectionTestUtils.setField(user, "id", 3L);
        postulante = new Postulante(user, "Ana Pérez", "12345678", "099111222", "San José");
        ReflectionTestUtils.setField(postulante, "id", 7L);
        perfil = new PerfilLaboral(postulante, "Ventas", DisponibilidadHoraria.FULL_TIME);
        ReflectionTestUtils.setField(perfil, "id", 11L);
        postulante.getPerfiles().add(perfil);

        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(postulantes.findByUsuarioId(3L)).thenReturn(Optional.of(postulante));
        when(postulantes.findById(7L)).thenReturn(Optional.of(postulante));
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), any(), any())).thenReturn(1);
        extractorDevuelve(SQL_PERFIL_POR_ID, true, 11L);
        when(cvs.findByPostulanteIdAndPerfilLaboralId(7L, 11L)).thenReturn(Optional.empty());
        when(cvs.saveAndFlush(any(Cv.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignedDownloadUrl(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> "https://descarga/" + invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cargaPdfConFirmaDeCuatroArgumentosYCompletaDatosDelPostulante() throws IOException {
        byte[] contenido = pdf("Curriculum de Ana", "Fecha de nacimiento: 12/05/1990",
                "Sexo: Femenino", "Estado civil: Soltera");

        CvResponse response = service().uploadCv(EMAIL,
                new MockMultipartFile("file", "Mi CV.PDF", "application/pdf", contenido), "Resumen", 11L);

        assertThat(response.perfilLaboralId()).isEqualTo(11L);
        assertThat(response.resumen()).isEqualTo("Resumen");
        assertThat(response.nombreArchivo()).isEqualTo("Mi CV.PDF");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.sizeBytes()).isEqualTo(contenido.length);
        assertThat(response.fechaCarga()).isNotNull();
        assertThat(response.downloadUrl()).startsWith("https://descarga/postulantes/7/cvs/").endsWith(".pdf");
        assertThat(postulante.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(postulante.getGenero()).isEqualTo(Genero.FEMENINO);
        assertThat(postulante.getEstadoCivil()).isEqualTo(EstadoCivil.SOLTERO);
        verify(storage).putObject(anyString(), any(MultipartFile.class));
        // Sin CV previo no hay ruta anterior para encolar.
        verify(jdbc, never()).update(contains("cv_archivos_pendientes_borrado"), (Object) any());
    }

    static Stream<Arguments> textosDeCv() {
        return Stream.of(
                Arguments.of("Sexo: prefiero no especificar. Estado civil: union libre", Genero.PREFIERO_NO_ESPECIFICAR,
                        EstadoCivil.UNION_LIBRE),
                // Con tildes y mayúsculas también se detecta.
                Arguments.of("SEXO: FEMENINO. Estado civil: Unión Libre", Genero.FEMENINO, EstadoCivil.UNION_LIBRE),
                Arguments.of("Sexo: no especifica. Divorciado", Genero.PREFIERO_NO_ESPECIFICAR, EstadoCivil.DIVORCIADO),
                Arguments.of("Mujer, divorciada", Genero.FEMENINO, EstadoCivil.DIVORCIADO),
                Arguments.of("Masculino, viudo", Genero.MASCULINO, EstadoCivil.VIUDO),
                Arguments.of("Hombre, viuda", Genero.MASCULINO, EstadoCivil.VIUDO),
                Arguments.of("Casado", null, EstadoCivil.CASADO),
                Arguments.of("Casada", null, EstadoCivil.CASADO),
                Arguments.of("Soltero", null, EstadoCivil.SOLTERO),
                Arguments.of("Experiencia en ventas", null, null));
    }

    @ParameterizedTest
    @MethodSource("textosDeCv")
    void detectaGeneroYEstadoCivilEnElTextoDelPdf(String texto, Genero genero, EstadoCivil estadoCivil)
            throws IOException {
        service().uploadCv(EMAIL, archivoPdf(pdf(texto)), null, 11L);

        assertThat(postulante.getGenero()).isEqualTo(genero);
        assertThat(postulante.getEstadoCivil()).isEqualTo(estadoCivil);
        assertThat(postulante.getFechaNacimiento()).isNull();
    }

    static Stream<Arguments> fechasDeNacimiento() {
        return Stream.of(
                Arguments.of("Nacimiento 03-11-1985", LocalDate.of(1985, 11, 3)),
                Arguments.of("F. Nac. 7.1.2001", LocalDate.of(2001, 1, 7)),
                // Coincide con el patrón pero ningún formato la acepta: queda vacía.
                Arguments.of("Fecha de nacimiento: 40/01/1990", null),
                Arguments.of("Fecha de nacimiento: 12/05.1990", null));
    }

    @ParameterizedTest
    @MethodSource("fechasDeNacimiento")
    void interpretaFechaDeNacimientoEnLosFormatosSoportados(String texto, LocalDate esperada) throws IOException {
        service().uploadCv(EMAIL, archivoPdf(pdf(texto)), null, 11L);

        assertThat(postulante.getFechaNacimiento()).isEqualTo(esperada);
    }

    @Test
    void noSobrescribeDatosYaCargadosNiLeeElPdfSiEstanCompletos() throws IOException {
        postulante.setFechaNacimiento(LocalDate.of(1980, 1, 1));
        postulante.setGenero(Genero.MASCULINO);
        byte[] contenido = pdf("Fecha de nacimiento: 12/05/1990 Mujer casada");

        service().uploadCv(EMAIL, archivoPdf(contenido), null, 11L);
        assertThat(postulante.getFechaNacimiento()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(postulante.getGenero()).isEqualTo(Genero.MASCULINO);
        assertThat(postulante.getEstadoCivil()).isEqualTo(EstadoCivil.CASADO);

        postulante.setEstadoCivil(EstadoCivil.VIUDO);
        service().uploadCv(EMAIL, archivoPdf(pdf("Soltera")), null, 11L);
        assertThat(postulante.getEstadoCivil()).isEqualTo(EstadoCivil.VIUDO);
    }

    @Test
    void completaSoloElGeneroCuandoEsElUnicoDatoFaltante() throws IOException {
        postulante.setFechaNacimiento(LocalDate.of(1980, 1, 1));
        postulante.setEstadoCivil(EstadoCivil.DIVORCIADO);

        service().uploadCv(EMAIL, archivoPdf(pdf("Fecha de nacimiento: 12/05/1990", "Hombre soltero")), null, 11L);

        assertThat(postulante.getGenero()).isEqualTo(Genero.MASCULINO);
        assertThat(postulante.getFechaNacimiento()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(postulante.getEstadoCivil()).isEqualTo(EstadoCivil.DIVORCIADO);
    }

    @Test
    void pdfIlegibleNoBloqueaLaCargaYDocxNoSeAnaliza() throws IOException {
        CvResponse pdfRoto = service().uploadCv(EMAIL,
                archivoPdf("%PDF-1.4 sin estructura valida".getBytes()), null, 11L);
        assertThat(pdfRoto.mimeType()).isEqualTo("application/pdf");
        assertThat(postulante.getGenero()).isNull();

        CvResponse docx = service().uploadCv(EMAIL,
                new MockMultipartFile("file", "cv.docx", DOCX_MIME, docx()), null, 11L);
        assertThat(docx.mimeType()).isEqualTo(DOCX_MIME);
        assertThat(docx.downloadUrl()).endsWith(".docx");
        assertThat(postulante.getFechaNacimiento()).isNull();
    }

    @Test
    void listaDeMimeVaciaOInvalidaUsaElValorPorDefecto() throws IOException {
        for (String raw : new String[] {null, "  ", " , ,"}) {
            CvService service = new CvService(postulantes, users, cvs, storage, jdbc, 10, 15, raw);
            assertThat(service.uploadCv(EMAIL, new MockMultipartFile("file", "cv.docx", DOCX_MIME, docx()), null, 11L)
                    .mimeType()).isEqualTo(DOCX_MIME);
        }
    }

    @Test
    void rechazaTipoDetectadoQueNoEstaPermitido() throws IOException {
        CvService soloPdf = new CvService(postulantes, users, cvs, storage, jdbc, 10, 15, "APPLICATION/PDF");
        MockMultipartFile word = new MockMultipartFile("file", "cv.docx", DOCX_MIME, docx());

        assertStatus(HttpStatus.BAD_REQUEST, () -> soloPdf.uploadCv(EMAIL, word, null, 11L));
        verify(storage, never()).putObject(anyString(), any(MultipartFile.class));
    }

    @Test
    void rechazaArchivoQueNoSePuedeLeer() throws IOException {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getSize()).thenReturn(10L);
        when(roto.getBytes()).thenThrow(new IOException("disco"));

        ResponseStatusException error = assertStatus(HttpStatus.BAD_REQUEST,
                () -> service().uploadCv(EMAIL, roto, null, 11L));
        assertThat(error.getReason()).isEqualTo("No se pudo leer el archivo de CV");
    }

    @Test
    void calculaLaExtensionDeLaRutaSegunElNombreOriginal() throws IOException {
        byte[] contenido = "%PDF-1.4".getBytes();
        assertThat(rutaSubida(archivoConNombre(null, contenido))).matches("postulantes/7/cvs/[0-9a-f-]{36}");
        assertThat(rutaSubida(new MockMultipartFile("file", "curriculum", "application/pdf", contenido)))
                .matches("postulantes/7/cvs/[0-9a-f-]{36}");
        assertThat(rutaSubida(new MockMultipartFile("file", "cv.extensionlarguisima", "application/pdf", contenido)))
                .matches("postulantes/7/cvs/[0-9a-f-]{36}");
        assertThat(rutaSubida(new MockMultipartFile("file", "cv.Pdf", "application/pdf", contenido)))
                .endsWith(".pdf");
    }

    @Test
    void reemplazoEncolaLaRutaAnteriorSoloSiNoEstaVacia() throws IOException {
        Cv existente = new Cv(postulante);
        existente.setRutaArchivoCv("postulantes/7/cvs/anterior.pdf");
        when(cvs.findByPostulanteIdAndPerfilLaboralId(7L, 11L)).thenReturn(Optional.of(existente));

        assertStatus(HttpStatus.CONFLICT, () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 11L));

        service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), "Nuevo", 11L, true);
        verify(jdbc).update("INSERT INTO cv_archivos_pendientes_borrado (ruta) VALUES (?) ON CONFLICT DO NOTHING",
                "postulantes/7/cvs/anterior.pdf");
        assertThat(existente.getRutaArchivoCv()).isNotEqualTo("postulantes/7/cvs/anterior.pdf");
        assertThat(existente.getResumen()).isEqualTo("Nuevo");

        Cv sinRuta = new Cv(postulante);
        sinRuta.setRutaArchivoCv("   ");
        when(cvs.findByPostulanteIdAndPerfilLaboralId(7L, 11L)).thenReturn(Optional.of(sinRuta));
        service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 11L, true);
        verify(jdbc, never()).update(anyString(), eq("   "));
    }

    @Test
    void fallaDeS3DevuelveBadGatewayYNoGuarda() {
        doThrow(new IllegalStateException("s3 caído")).when(storage).putObject(anyString(), any(MultipartFile.class));

        assertStatus(HttpStatus.BAD_GATEWAY,
                () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 11L));
        verify(cvs, never()).saveAndFlush(any());
    }

    @Test
    void siLaTransaccionNoConfirmaBorraElArchivoSubidoYToleraErroresAlBorrar() {
        String ruta = rutaSubida(archivoPdf("%PDF-1.4".getBytes()));
        List<TransactionSynchronization> sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
        assertThat(sincronizaciones).hasSize(1);
        TransactionSynchronization limpieza = sincronizaciones.getFirst();

        limpieza.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).deleteObject(anyString());

        limpieza.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteObject(ruta);

        doThrow(new IllegalStateException("s3 caído")).when(storage).deleteObject(ruta);
        limpieza.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        verify(storage, org.mockito.Mockito.times(2)).deleteObject(ruta);
    }

    @Test
    void sinPerfilIndicadoUsaElPrimeroDelPostulanteOExigeCrearUno() {
        extractorDevuelve("ORDER BY id LIMIT 1", true, 11L);
        CvResponse response = service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, null);
        assertThat(response.perfilLaboralId()).isEqualTo(11L);

        extractorDevuelve("ORDER BY id LIMIT 1", false, 0L);
        ResponseStatusException error = assertStatus(HttpStatus.BAD_REQUEST,
                () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, null));
        assertThat(error.getReason()).isEqualTo("Debes crear un perfil profesional antes de subir un CV");
    }

    @Test
    void perfilAjenoOInexistenteDevuelveNotFound() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(98L), any())).thenReturn(0);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(99L), any())).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 98L));
        assertStatus(HttpStatus.NOT_FOUND, () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 99L));

        // El perfil existe en la tabla pero no figura entre los perfiles cargados del postulante.
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(12L), any())).thenReturn(1);
        assertStatus(HttpStatus.NOT_FOUND, () -> service().uploadCv(EMAIL, archivoPdf("%PDF-1.4".getBytes()), null, 12L));
        verify(storage, never()).putObject(anyString(), any(MultipartFile.class));
    }

    @Test
    void resuelvePostulantePorEmailOPorConsultaDeRespaldo() {
        when(postulantes.findByUsuarioId(3L)).thenReturn(Optional.empty());
        when(postulantes.findByUsuarioEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(postulante));
        when(cvs.findByPostulanteIdOrderByVersionDesc(7L)).thenReturn(List.of());
        assertThat(service().listMyCvs(EMAIL)).isEmpty();

        when(postulantes.findByUsuarioEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        extractorDevuelve("JOIN usuarios u", true, 7L);
        Cv cv = new Cv(postulante);
        cv.setRutaArchivoCv("postulantes/7/cvs/a.pdf");
        when(cvs.findByPostulanteIdOrderByVersionDesc(7L)).thenReturn(List.of(cv));
        List<CvResponse> respaldo = service().listMyCvs(EMAIL);
        assertThat(respaldo).singleElement()
                .satisfies(r -> {
                    assertThat(r.perfilLaboralId()).isNull();
                    assertThat(r.downloadUrl()).isEqualTo("https://descarga/postulantes/7/cvs/a.pdf");
                });

        // La consulta encuentra un id pero el repositorio ya no lo tiene.
        extractorDevuelve("JOIN usuarios u", true, 70L);
        when(postulantes.findById(70L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.FORBIDDEN, () -> service().listMyCvs(EMAIL));

        extractorDevuelve("JOIN usuarios u", false, 0L);
        assertStatus(HttpStatus.FORBIDDEN, () -> service().listMyCvs(EMAIL));
    }

    private CvService service() {
        return new CvService(postulantes, users, cvs, storage, jdbc, 2, 15, "application/pdf," + DOCX_MIME);
    }

    private String rutaSubida(MultipartFile file) {
        org.mockito.Mockito.clearInvocations(storage);
        service().uploadCv(EMAIL, file, null, 11L);
        ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
        verify(storage).putObject(ruta.capture(), any(MultipartFile.class));
        return ruta.getValue();
    }

    @SuppressWarnings("unchecked")
    private void extractorDevuelve(String fragmentoSql, boolean hayFila, long valor) {
        when(jdbc.query(contains(fragmentoSql), any(ResultSetExtractor.class), any())).thenAnswer(invocation -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(hayFila);
            when(rs.getLong(1)).thenReturn(valor);
            return ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(rs);
        });
    }

    private static MultipartFile archivoConNombre(String nombre, byte[] contenido) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) contenido.length);
        when(file.getBytes()).thenReturn(contenido);
        when(file.getOriginalFilename()).thenReturn(nombre);
        return file;
    }

    private static MockMultipartFile archivoPdf(byte[] contenido) {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", contenido);
    }

    private static byte[] pdf(String... lineas) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (String linea : lineas) {
                    stream.showText(linea);
                    stream.newLineAtOffset(0, -15);
                }
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static ResponseStatusException assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException[] capturada = new ResponseStatusException[1];
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(expected);
            capturada[0] = e;
        });
        return capturada[0];
    }
}
