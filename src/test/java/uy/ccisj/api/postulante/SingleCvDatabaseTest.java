package uy.ccisj.api.postulante;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "singleCvTestJdbcUrl", matches = ".+")
class SingleCvDatabaseTest {
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory emf;
    private static JdbcTemplate jdbc;
    private static Long applicantId;
    private static Long legacyProfile;
    private static Long offerId;
    private static Long retainedCv;
    private static final String EMAIL = "single-cv@example.test";
    private CvService service;
    private S3StorageService storage;
    private TransactionTemplate tx;
    private Long profile;

    @BeforeAll
    static void migrateLegacyData() {
        String schema = "single_cv_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var ds = new DriverManagerDataSource(System.getProperty("singleCvTestJdbcUrl") + "?currentSchema=" + schema + ",public",
                "cv_test", "cv_test");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(ds).schemas(schema).target("33").load().migrate();
        Long user = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, 'test', 'POSTULANTE') RETURNING id",
                Long.class, EMAIL);
        applicantId = jdbc.queryForObject("""
                INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia)
                VALUES (?, 'CV Test', 'single-cv-test', '091111111', 'Test') RETURNING id
                """, Long.class, user);
        legacyProfile = newProfile();
        Long obsolete = insertLegacyCv(legacyProfile, "legacy-old.pdf", false, 1);
        retainedCv = insertLegacyCv(legacyProfile, "legacy-selected.pdf", true, 2);
        insertLegacyCv(legacyProfile, "legacy-newer.pdf", false, 3);
        Long socioUser = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol, bps) VALUES ('single-cv-socio@example.test', 'test', 'SOCIO', 'single-cv') RETURNING id", Long.class);
        Long socio = jdbc.queryForObject("INSERT INTO socios (usuario_id, razon_social) VALUES (?, 'Test') RETURNING id", Long.class, socioUser);
        offerId = jdbc.queryForObject("""
                INSERT INTO ofertas_empleo (socio_id, rubro_id, titulo, descripcion)
                VALUES (?, (SELECT id FROM rubros_empleo LIMIT 1), 'Test', 'Test') RETURNING id
                """, Long.class, socio);
        jdbc.update("INSERT INTO postulaciones (postulante_id, oferta_id, perfil_laboral_id, cv_id) VALUES (?, ?, ?, ?)",
                applicantId, offerId, legacyProfile, obsolete);
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM cv_archivos_pendientes_borrado WHERE ruta LIKE 'legacy-%'", Long.class));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        emf = factory.getObject();
    }

    @BeforeEach
    void setUp() {
        var repos = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        storage = mock(S3StorageService.class);
        when(storage.presignedDownloadUrl(anyString(), any())).thenAnswer(i -> "https://example.test/" + i.getArgument(0));
        service = new CvService(repos.getRepository(PostulanteRepository.class), repos.getRepository(UserRepository.class),
                repos.getRepository(CvRepository.class), storage, jdbc, 10, 15, "application/pdf");
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        profile = newProfile();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void migrationKeepsSelectedDocumentAndRepointsApplications() {
        assertEquals(List.of(retainedCv), jdbc.queryForList("SELECT id FROM cvs WHERE perfil_laboral_id = ?", Long.class, legacyProfile));
        assertEquals(retainedCv, jdbc.queryForObject("SELECT cv_id FROM postulaciones WHERE oferta_id = ?", Long.class, offerId));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertLegacyCv(legacyProfile, "duplicate.pdf", true, 1));
    }

    @Test
    void replacesInPlaceRequiresConfirmationAndAllowsDeleteThenNewUpload() {
        Long linkedOffer = jdbc.queryForObject("""
                INSERT INTO ofertas_empleo (socio_id, rubro_id, titulo, descripcion)
                SELECT socio_id, rubro_id, 'Replacement test', 'Test' FROM ofertas_empleo WHERE id = ? RETURNING id
                """, Long.class, offerId);
        CvResponse first = upload("original.pdf", false);
        assertEquals(1, count());
        var conflict = assertThrows(ResponseStatusException.class, () -> upload("unconfirmed.pdf", false));
        assertEquals(409, conflict.getStatusCode().value());
        verify(storage, times(1)).putObject(anyString(), any());
        String originalKey = key(first.id());
        CvResponse replaced = upload("manual.pdf", true);
        assertEquals(first.id(), replaced.id());
        assertEquals(1, count());
        assertEquals("manual.pdf", inTx(() -> service.listMyCvs(EMAIL)).stream().filter(c -> c.id().equals(first.id())).findFirst().orElseThrow().nombreArchivo());
        assertNotEquals(originalKey, key(first.id()));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM cv_archivos_pendientes_borrado WHERE ruta = ?", Long.class, originalKey));
        jdbc.update("INSERT INTO postulaciones (postulante_id, oferta_id, perfil_laboral_id, cv_id) VALUES (?, ?, ?, ?)",
                applicantId, linkedOffer, profile, first.id());
        upload("updated.pdf", true);
        assertEquals(first.id(), jdbc.queryForObject("SELECT cv_id FROM postulaciones WHERE oferta_id = ?", Long.class, linkedOffer));
        tx.executeWithoutResult(status -> service.deleteCv(EMAIL, first.id()));
        assertEquals(0, count());
        assertNull(jdbc.queryForObject("SELECT cv_id FROM postulaciones WHERE oferta_id = ?", Long.class, linkedOffer));
        assertNotEquals(first.id(), upload("new.pdf", false).id());
    }

    @Test
    void failedStorageAndRollbackPreserveOriginalCv() {
        CvResponse first = upload("original.pdf", false);
        String original = key(first.id());
        doThrow(new IllegalStateException("Simulated storage error")).when(storage).putObject(anyString(), any());
        assertThrows(ResponseStatusException.class, () -> upload("failed.pdf", true));
        assertEquals(original, key(first.id()));
        assertEquals(1, count());
        doNothing().when(storage).putObject(anyString(), any());
        tx.executeWithoutResult(status -> {
            service.uploadCv(EMAIL, file("rollback.pdf"), null, profile, true);
            status.setRollbackOnly();
        });
        assertEquals(original, key(first.id()));
        verify(storage).deleteObject(argThat(k -> !k.equals(original)));
    }

    @Test
    void simultaneousUploadsCannotCreateTwoCvsAndOtherAccountsCannotReplace() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var requests = java.util.stream.IntStream.range(0, 2).mapToObj(index -> pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    upload("parallel-" + index + ".pdf", false);
                    return 201;
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            })).toList();
            start.countDown();
            var statuses = List.of(requests.get(0).get(20, TimeUnit.SECONDS), requests.get(1).get(20, TimeUnit.SECONDS));
            assertTrue(statuses.contains(201));
            assertTrue(statuses.contains(409));
        }
        assertEquals(1, count());
        assertThrows(ResponseStatusException.class, () -> inTx(() ->
                service.uploadCv("single-cv-socio@example.test", file("not-owned.pdf"), null, profile, true)));
        assertEquals(1, count());
    }

    @Test
    void cleanupRetriesFailureAndDoesNotDeleteCurrentFiles() {
        String key = "test-cleanup-" + profile;
        jdbc.update("INSERT INTO cv_archivos_pendientes_borrado (ruta) VALUES (?)", key);
        doThrow(new IllegalStateException("Simulated delete error")).when(storage).deleteObject(key);
        var cleanup = new CvFileCleanup(jdbc, storage);
        cleanup.deletePendingFiles();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM cv_archivos_pendientes_borrado WHERE ruta = ?", Long.class, key));
        doNothing().when(storage).deleteObject(key);
        CvResponse first = upload("current.pdf", false);
        String current = key(first.id());
        jdbc.update("INSERT INTO cv_archivos_pendientes_borrado (ruta) VALUES (?)", current);
        cleanup.deletePendingFiles();
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM cv_archivos_pendientes_borrado WHERE ruta = ?", Long.class, key));
        verify(storage, never()).deleteObject(current);
    }

    private static Long newProfile() {
        return jdbc.queryForObject("INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria) VALUES (?, 'Test', 'FULL_TIME') RETURNING id",
                Long.class, applicantId);
    }

    private static Long insertLegacyCv(Long profile, String key, boolean active, int version) {
        return jdbc.queryForObject("""
                INSERT INTO cvs (postulante_id, perfil_laboral_id, ruta_archivo_cv, activo, version)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, applicantId, profile, key, active, version);
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private CvResponse upload(String name, boolean replace) {
        return inTx(() -> service.uploadCv(EMAIL, file(name), null, profile, replace));
    }

    private <T> T inTx(Supplier<T> operation) {
        return tx.execute(status -> operation.get());
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM cvs WHERE perfil_laboral_id = ?", Integer.class, profile);
    }

    private String key(Long id) {
        return jdbc.queryForObject("SELECT ruta_archivo_cv FROM cvs WHERE id = ?", String.class, id);
    }
}
