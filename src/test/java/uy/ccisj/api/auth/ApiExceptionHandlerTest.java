package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void usaPrimerErrorDeValidacionYSuFallback() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "Correo inválido"));
        var error = new MethodArgumentNotValidException(mock(MethodParameter.class), binding);
        assertResponse(400, "Correo inválido", handler.handleValidation(error));

        var empty = new MethodArgumentNotValidException(
                mock(MethodParameter.class), new BeanPropertyBindingResult(new Object(), "request"));
        assertResponse(400, "Datos inválidos", handler.handleValidation(empty));
    }

    @Test
    void conservaEstadoYUsaMensajeGenericoSinReason() {
        assertResponse(404, "No existe",
                handler.handleStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe")));
        assertResponse(500, "No fue posible completar la solicitud.",
                handler.handleStatus(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    @ParameterizedTest
    @CsvSource({
            "'No se pudo convertir LocalDate', 'La fecha no tiene un formato válido. Usa AAAA-MM-DD.'",
            "'fecha inválida', 'La fecha no tiene un formato válido. Usa AAAA-MM-DD.'",
            "'JSON malformed', 'Los datos enviados no tienen un formato válido.'"
    })
    void traduceCuerpoIlegible(String cause, String expected) {
        HttpMessageNotReadableException error = mock(HttpMessageNotReadableException.class);
        when(error.getMostSpecificCause()).thenReturn(new RuntimeException(cause));
        assertResponse(400, expected, handler.handleUnreadable(error));
    }

    @ParameterizedTest
    @CsvSource({
            "'duplicate email', 'Ya existe una cuenta con ese correo'",
            "'duplicate bps', 'Ya existe un socio con ese BPS'",
            "'duplicate cedula', 'Ya existe un socio con esa cédula'",
            "'duplicate rut', 'Ya existe un socio con ese RUT'",
            "'other constraint', 'Los datos chocan con un registro existente'"
    })
    void traduceConflictosDeIntegridad(String cause, String expected) {
        var error = new DataIntegrityViolationException("constraint", new RuntimeException(cause));
        assertResponse(409, expected, handler.handleConflict(error));
    }

    private void assertResponse(
            int status,
            String detail,
            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> response) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(detail, response.getBody().get("detail"));
        assertEquals(status, response.getBody().get("status"));
    }
}
