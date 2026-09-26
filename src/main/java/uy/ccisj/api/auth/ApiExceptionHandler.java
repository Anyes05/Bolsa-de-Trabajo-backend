package uy.ccisj.api.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> Optional.ofNullable(error.getDefaultMessage()).orElse("Datos inválidos"))
                .orElse("Datos inválidos");
        return body(HttpStatus.BAD_REQUEST.value(), detail);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException exception) {
        String detail = Optional.ofNullable(exception.getReason()).orElse("No fue posible completar la solicitud.");
        return body(exception.getStatusCode().value(), detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        String message = Optional.ofNullable(exception.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse("")
                .toLowerCase();
        String detail = message.contains("localdate") || message.contains("fecha")
                ? "La fecha no tiene un formato válido. Usa AAAA-MM-DD."
                : "Los datos enviados no tienen un formato válido.";
        return body(HttpStatus.BAD_REQUEST.value(), detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException exception) {
        String message = Optional.ofNullable(exception.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse("")
                .toLowerCase();
        if (message.contains("email")) {
            return body(HttpStatus.CONFLICT.value(), "Ya existe una cuenta con ese correo");
        }
        if (message.contains("bps")) {
            return body(HttpStatus.CONFLICT.value(), "Ya existe un socio con ese BPS");
        }
        if (message.contains("rut")) {
            return body(HttpStatus.CONFLICT.value(), "Ya existe un socio con ese RUT");
        }
        return body(HttpStatus.CONFLICT.value(), "Los datos chocan con un registro existente");
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("detail", detail);
        return ResponseEntity.status(status).body(payload);
    }
}
