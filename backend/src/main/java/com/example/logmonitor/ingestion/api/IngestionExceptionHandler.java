package com.example.logmonitor.ingestion.api;

import com.example.logmonitor.ingestion.application.IngestionValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = IngestionController.class)
public class IngestionExceptionHandler {

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Map<String, Object>> payloadTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body exceeds the configured maximum size");
    }

    @ExceptionHandler(IngestionValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(IngestionValidationException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> beanValidation() {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody(HttpMessageNotReadableException exception) {
        if (hasCause(exception, PayloadTooLargeException.class)) {
            return payloadTooLarge();
        }
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", false);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(body);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
