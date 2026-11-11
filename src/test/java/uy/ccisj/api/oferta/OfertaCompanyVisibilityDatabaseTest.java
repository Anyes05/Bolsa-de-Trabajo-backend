package uy.ccisj.api.oferta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.mail.MailNotificationService;
import uy.ccisj.api.mail.MailTemplate;
import uy.ccisj.api.notificacion.NotificacionRepository;
import uy.ccisj.api.postulante.CvRepository;
import uy.ccisj.api.postulante.PostulanteRepository;
import uy.ccisj.api.socio.SocioBolsaController;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.storage.S3StorageService;
import uy.ccisj.api.user.UserRepository;

@EnabledIfSystemProperty(named = "offerVisibilityTestJdbcUrl", matches = ".+")
class OfertaCompanyVisibilityDatabaseTest {
    private static final String COMPANY = "Empresa de prueba con nombre reservado";
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static Long rubroId;
    private OfertaEmpleoService offers;
    private PostulacionService applications;
    private SocioBolsaController contact;
    private MailNotificationService mail;
    private TransactionTemplate tx;
    private Long socioId;
    private Long applicantUserId;
    private Long profileId;
    private String socioEmail;
    private String applicantEmail;

    @BeforeAll
    static void migrateExistingOffer() {
        String schema = "offer_visibility_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var ds = new DriverManagerDataSource(System.getProperty("offerVisibilityTestJdbcUrl") + "?currentSchema=" + schema + ",public",
                "offers_test", "offers_test");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
        Flyway.configure().dataSource(ds).schemas(schema).target("36").load().migrate();
        rubroId = jdbc.queryForObject("SELECT id FROM rubros_empleo WHERE activo = TRUE LIMIT 1", Long.class);
        Long user = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol, bps) VALUES ('legacy-offer@example.test', 'test', 'SOCIO', 'legacy-offer') RETURNING id", Long.class);
        Long socio = jdbc.queryForObject("INSERT INTO socios (usuario_id, razon_social) VALUES (?, 'Legacy company') RETURNING id", Long.class, user);
        Long legacy = jdbc.queryForObject("INSERT INTO ofertas_empleo (socio_id, rubro_id, titulo, descripcion) VALUES (?, ?, 'Legacy offer', 'Description') RETURNING id", Long.class, socio, rubroId);
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        assertTrue(jdbc.queryForObject("SELECT mostrar_nombre_empresa FROM ofertas_empleo WHERE id = ?", Boolean.class, legacy));
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("uy.ccisj.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
    }

    @BeforeEach
    void setUp() {
        var emf = factory.getObject();
        var em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repos = new JpaRepositoryFactory(em);
        var offerRepo = repos.getRepository(OfertaEmpleoRepository.class);
        var socios = repos.getRepository(SocioRepository.class);
        var users = repos.getRepository(UserRepository.class);
        offers = new OfertaEmpleoService(offerRepo, socios, repos.getRepository(RubroEmpleoRepository.class), users);
        applications = new PostulacionService(offerRepo, repos.getRepository(PostulanteRepository.class),
                repos.getRepository(PostulacionRepository.class), repos.getRepository(CvRepository.class), offers, em);
        mail = mock(MailNotificationService.class);
        contact = new SocioBolsaController(jdbc, users, socios, mock(S3StorageService.class), mail,
                repos.getRepository(NotificacionRepository.class), 15);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        String key = java.util.UUID.randomUUID().toString().substring(0, 24);
        socioEmail = key + "-socio@example.test";
        applicantEmail = key + "-applicant@example.test";
        Long socioUserId = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol, bps) VALUES (?, 'test', 'SOCIO', ?) RETURNING id", Long.class, socioEmail, key);
        socioId = jdbc.queryForObject("INSERT INTO socios (usuario_id, razon_social) VALUES (?, ?) RETURNING id", Long.class, socioUserId, COMPANY);
        applicantUserId = jdbc.queryForObject("INSERT INTO usuarios (email, password_hash, rol) VALUES (?, 'test', 'POSTULANTE') RETURNING id", Long.class, applicantEmail);
        Long applicantId = jdbc.queryForObject("""
                INSERT INTO postulantes (usuario_id, nombre_completo, cedula_identidad, telefono, zona_residencia)
                VALUES (?, 'Postulante de prueba', ?, '091111111', 'San Jose') RETURNING id
                """, Long.class, applicantUserId, key);
        profileId = jdbc.queryForObject("INSERT INTO perfiles_laborales (postulante_id, nombre, disponibilidad_horaria) VALUES (?, 'Administracion', 'FULL_TIME') RETURNING id", Long.class, applicantId);
        jdbc.update("INSERT INTO cvs (postulante_id, perfil_laboral_id, ruta_archivo_cv, activo, version) VALUES (?, ?, 'test.pdf', TRUE, 1)", applicantId, profileId);
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void defaultShowsCompanyForExistingOffersAndNewRequestsWithoutFlag() throws Exception {
        var request = new ObjectMapper().readValue("""
                {"rubroId":%d,"titulo":"Asistente","descripcion":"Tareas administrativas","vacantes":1}
                """.formatted(rubroId), OfertaRequest.class);
        var created = inTx(() -> offers.create(socioEmail, false, request));
        assertTrue(created.mostrarNombreEmpresa());
        assertEquals(COMPANY, forApplicant(created.id()).socioNombre());
        assertTrue(jdbc.queryForObject("SELECT mostrar_nombre_empresa FROM ofertas_empleo WHERE id = ?", Boolean.class, created.id()));
    }

    @Test
    void hidesOnlyApplicantResponsesAndPersistsEditsAcrossTransactions() throws Exception {
        var hidden = inTx(() -> offers.create(socioEmail, false, request(false)));
        assertFalse(hidden.mostrarNombreEmpresa());
        assertEquals(COMPANY, hidden.socioNombre());
        assertEquals("Empresa reservada", forApplicant(hidden.id()).socioNombre());
        assertEquals(COMPANY, inTx(() -> offers.list(socioEmail, false)).get(0).socioNombre());
        assertEquals(COMPANY, inTx(() -> offers.list("admin@example.test", true)).stream()
                .filter(o -> o.id().equals(hidden.id())).findFirst().orElseThrow().socioNombre());
        var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(forApplicant(hidden.id()));
        assertFalse(json.contains(COMPANY));
        assertFalse(jdbc.queryForObject("SELECT mostrar_nombre_empresa FROM ofertas_empleo WHERE id = ?", Boolean.class, hidden.id()));
        inTx(() -> offers.update(socioEmail, false, hidden.id(), request(null)));
        assertFalse(forApplicant(hidden.id()).mostrarNombreEmpresa(), "Omitir el campo no debe deshacer la reserva");
        inTx(() -> offers.update(socioEmail, false, hidden.id(), request(true)));
        assertEquals(COMPANY, forApplicant(hidden.id()).socioNombre());
    }

    @Test
    void applicationResponseAndMyApplicationsRespectReservation() {
        var hidden = inTx(() -> offers.create(socioEmail, false, request(false)));
        assertEquals("Empresa reservada", inTx(() -> applications.apply(applicantEmail, hidden.id(), profileId)).socioNombre());
        assertEquals("Empresa reservada", inTx(() -> applications.listMine(applicantEmail)).get(0).socioNombre());
        inTx(() -> offers.update(socioEmail, false, hidden.id(), request(true)));
        assertEquals(COMPANY, inTx(() -> applications.listMine(applicantEmail)).get(0).socioNombre());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notificationsAndEmailsStillIdentifyTheRealCompanyForHiddenOffers(boolean selected) {
        var hidden = inTx(() -> offers.create(socioEmail, false, request(false)));
        inTx(() -> applications.apply(applicantEmail, hidden.id(), profileId));
        var auth = new UsernamePasswordAuthenticationToken(socioEmail, "test");
        inTx(() -> contact.notifyApplicant(auth, profileId,
                new SocioBolsaController.ContactNotificationRequest(selected, hidden.id())));
        String body = jdbc.queryForObject("SELECT cuerpo FROM notificaciones WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
                String.class, applicantUserId);
        assertTrue(body.contains(COMPANY));
        assertFalse(body.contains("Empresa reservada"));
        var content = ArgumentCaptor.forClass(MailTemplate.Content.class);
        verify(mail).send(eq(applicantEmail), anyString(), content.capture(), eq(socioEmail));
        assertTrue(content.getValue().plain().contains(COMPANY));
        assertTrue(content.getValue().html().contains(COMPANY));
        assertEquals("Empresa reservada", forApplicant(hidden.id()).socioNombre());
    }

    @Test
    void anotherSocioCannotChangeVisibility() {
        var hidden = inTx(() -> offers.create(socioEmail, false, request(false)));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> inTx(() -> offers.update("legacy-offer@example.test", false, hidden.id(), request(true))))
                .getStatusCode().value());
        assertFalse(forApplicant(hidden.id()).mostrarNombreEmpresa());
    }

    private OfertaResponse forApplicant(Long id) {
        return inTx(() -> offers.listForApplicants(rubroId)).stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    private OfertaRequest request(Boolean show) {
        return new OfertaRequest(socioId, rubroId, "Asistente", null, "Tareas administrativas", null,
                "San Jose", null, null, DisponibilidadHoraria.FULL_TIME, 1, null, show);
    }

    private <T> T inTx(Supplier<T> operation) {
        return tx.execute(status -> operation.get());
    }
}
