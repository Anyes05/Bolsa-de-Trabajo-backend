package uy.ccisj.api.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RubroAdminServiceTest {
    private final RubroEmpleoRepository repository = mock(RubroEmpleoRepository.class);
    private RubroAdminService service;

    @BeforeEach
    void setUp() {
        service = new RubroAdminService(repository);
    }

    @Test
    void listaActivosOTodosSegunFiltro() {
        RubroEmpleo active = rubro(1L, "Comercio", true);
        RubroEmpleo inactive = rubro(2L, "Industria", false);
        when(repository.findByActivoTrueOrderByNombreRubroAsc()).thenReturn(List.of(active));
        when(repository.findAllByOrderByNombreRubroAsc()).thenReturn(List.of(active, inactive));

        assertEquals(List.of("Comercio"), service.list(false).stream().map(RubroAdminDTO::nombreRubro).toList());
        assertEquals(2, service.list(true).size());
    }

    @Test
    void creaNormalizandoEspaciosYRechazaDuplicadoOVacio() {
        when(repository.save(any(RubroEmpleo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("Tecnología de la información",
                service.create("  Tecnología   de la información  ").nombreRubro());

        when(repository.existsByNombreRubroIgnoreCase("Comercio")).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.create("Comercio"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(" "));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(null));
    }

    @Test
    void actualizaNombreYEstado() {
        RubroEmpleo rubro = rubro(1L, "Anterior", true);
        when(repository.findById(1L)).thenReturn(Optional.of(rubro));
        when(repository.save(rubro)).thenReturn(rubro);

        assertEquals("Nuevo nombre", service.update(1L, " Nuevo   nombre ").nombreRubro());
        assertEquals(false, service.setActivo(1L, false).activo());
        verify(repository).existsByNombreRubroIgnoreCaseAndIdNot("Nuevo nombre", 1L);
    }

    @Test
    void rechazaNombreAjenoDuplicadoYRubroInexistente() {
        RubroEmpleo rubro = rubro(1L, "Anterior", true);
        when(repository.findById(1L)).thenReturn(Optional.of(rubro));
        when(repository.existsByNombreRubroIgnoreCaseAndIdNot("Duplicado", 1L)).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.update(1L, "Duplicado"));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.setActivo(99L, false));
    }

    private RubroEmpleo rubro(Long id, String name, boolean active) {
        RubroEmpleo rubro = new RubroEmpleo(name);
        rubro.setActivo(active);
        return rubro;
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}
