package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

/**
 * Prueba la SecurityFilterChain real: reglas por rol, rutas publicas, CORS y respuestas JSON de 401/403.
 * El JwtAuthenticationFilter es el real, con JwtService y UserRepository simulados: el token "admin"
 * autentica a un ADMIN, "socio" a un SOCIO y "postulante" a un POSTULANTE.
 */
@SpringJUnitWebConfig(classes = {
        SecurityConfig.class,
        SecurityConfigCoverageTest.Infraestructura.class,
        SecurityConfigCoverageTest.ControladorDePrueba.class})
@TestPropertySource(properties = "app.cors.allowed-origin=http://localhost:5173")
class SecurityConfigCoverageTest {
    private static final String ORIGEN_LOCAL = "http://localhost:5173";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    static class Infraestructura {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            JwtService jwt = mock(JwtService.class);
            UserRepository users = mock(UserRepository.class);
            for (Role role : Role.values()) {
                String token = role.name().toLowerCase();
                when(jwt.isValid(token)).thenReturn(true);
                when(jwt.extractEmail(token)).thenReturn(token + "@example.test");
                when(jwt.sessionVersion(token)).thenReturn(0);
            }
            when(users.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> {
                String email = invocation.getArgument(0);
                for (Role role : Role.values()) {
                    if (email.equals(role.name().toLowerCase() + "@example.test")) {
                        return Optional.of(new User(email, null, "hash", role));
                    }
                }
                return Optional.empty();
            });
            return new JwtAuthenticationFilter(jwt, users);
        }
    }

    @RestController
    static class ControladorDePrueba {
        @GetMapping({"/admin/panel", "/socio/panel", "/postulante/panel", "/ofertas", "/ofertas/7",
                "/sectors", "/actuator/health", "/privado"})
        String leer() {
            return "ok";
        }

        @PostMapping({"/auth/login", "/auth/register", "/auth/register/availability", "/auth/google",
                "/auth/google/link", "/auth/permanencia", "/auth/permanencia/confirmar"})
        String publico() {
            return "publico";
        }
    }

    @BeforeEach
    void setUp() {
        Filter cadena = context.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(cadena).build();
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rutasPublicasNoRequierenTokenNiCsrf() throws Exception {
        mvc.perform(get("/sectors")).andExpect(status().isOk()).andExpect(content().string("ok"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        for (String ruta : new String[] {"/auth/login", "/auth/register", "/auth/register/availability",
                "/auth/google", "/auth/google/link", "/auth/permanencia", "/auth/permanencia/confirmar"}) {
            mvc.perform(post(ruta)).andExpect(status().isOk()).andExpect(content().string("publico"));
        }
    }

    @Test
    void sinTokenDevuelve401EnJson() throws Exception {
        mvc.perform(get("/admin/panel"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("No autenticado"))
                .andExpect(jsonPath("$.reason").value("InsufficientAuthenticationException"));
        // Un token invalido no autentica: la ruta privada sigue respondiendo 401.
        mvc.perform(get("/privado").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolIncorrectoDevuelve403EnJson() throws Exception {
        mvc.perform(get("/admin/panel").header("Authorization", "Bearer socio"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Acceso denegado"))
                .andExpect(jsonPath("$.reason").value("AccessDeniedException"));
    }

    @Test
    void cadaPrefijoExigeSuRol() throws Exception {
        esperar("/admin/panel", "admin", 200);
        esperar("/admin/panel", "postulante", 403);
        esperar("/socio/panel", "socio", 200);
        esperar("/socio/panel", "admin", 403);
        esperar("/postulante/panel", "postulante", 200);
        esperar("/postulante/panel", "socio", 403);
        esperar("/ofertas", "admin", 200);
        esperar("/ofertas/7", "socio", 200);
        esperar("/ofertas", "postulante", 403);
        esperar("/privado", "postulante", 200);
        esperar("/privado", "admin", 200);
    }

    @Test
    void laSesionEsStateless() throws Exception {
        MvcResult resultado = mvc.perform(get("/privado").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andReturn();
        assertNull(resultado.getRequest().getSession(false));
        assertNull(resultado.getResponse().getCookie("JSESSIONID"));
    }

    @Test
    void corsPermiteSoloOrigenesConfigurados() throws Exception {
        for (String origen : new String[] {ORIGEN_LOCAL, "https://ccisj.cloud-ip.cc"}) {
            mvc.perform(options("/admin/panel")
                            .header("Origin", origen)
                            .header("Access-Control-Request-Method", "PATCH")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origen));
        }
        mvc.perform(options("/admin/panel")
                        .header("Origin", "https://otro-sitio.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/admin/panel")
                        .header("Origin", ORIGEN_LOCAL)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordEncoderEsBcrypt() {
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
        String hash = passwordEncoder.encode("Clave-Segura1");
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwordEncoder.matches("Clave-Segura1", hash));
    }

    @Test
    void accessDeniedHandlerSinAutenticacionInformaAnonimo() throws Exception {
        FilterChainProxy proxy = context.getBean(FilterChainProxy.class);
        ExceptionTranslationFilter traduccion = proxy.getFilterChains().get(0).getFilters().stream()
                .filter(ExceptionTranslationFilter.class::isInstance)
                .map(ExceptionTranslationFilter.class::cast)
                .findFirst().orElseThrow();
        AccessDeniedHandler handler = (AccessDeniedHandler) ReflectionTestUtils.getField(traduccion, "accessDeniedHandler");
        assertNotNull(handler);
        SecurityContextHolder.clearContext();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/admin/panel"), response, new AccessDeniedException("x"));

        assertEquals(403, response.getStatus());
        assertEquals("{\"status\":403,\"detail\":\"Acceso denegado\",\"reason\":\"AccessDeniedException\"}",
                response.getContentAsString());
    }

    private void esperar(String ruta, String token, int estado) throws Exception {
        mvc.perform(get(ruta).header("Authorization", "Bearer " + token)).andExpect(status().is(estado));
        SecurityContextHolder.clearContext();
    }
}
