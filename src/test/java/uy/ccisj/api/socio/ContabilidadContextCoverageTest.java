package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Autor del asiento contable según quién origina el cambio. */
class ContabilidadContextCoverageTest {
    private final EntityManager em = mock(EntityManager.class);
    private final Query query = mock(Query.class);

    @BeforeEach
    void setUp() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private String autorRegistrado(String motivo) {
        ContabilidadContext.registrar(em, motivo);
        verify(query).setParameter("motivo", motivo);
        verify(query).getSingleResult();
        var autor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(query).setParameter(eq("autor"), autor.capture());
        return (String) autor.getValue();
    }

    @Test
    void adminAutenticadoFirmaConSuNombre() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertEquals("ADMIN:admin@example.test", autorRegistrado("Cobro recibido"));
    }

    @Test
    void sinAutenticacionLaFacturacionFirmaComoJob() {
        assertEquals("JOB:FACTURACION", autorRegistrado("Facturación del período 2026-05-01"));
    }

    @Test
    void usuarioNoAdminFirmaComoAplicacion() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "socio@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_SOCIO"))));

        assertEquals("SISTEMA:APLICACION", autorRegistrado("Cobro recibido"));
    }

    @Test
    void autenticacionNoConfirmadaNoSeConsideraAdmin() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals("SISTEMA:APLICACION", autorRegistrado("Registro de reintegro"));
        verify(auth, never()).getAuthorities();
    }

    @Test
    void esUnaClaseUtilitariaNoInstanciable() throws Exception {
        Constructor<ContabilidadContext> constructor = ContabilidadContext.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
