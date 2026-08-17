package com.example.logmonitor.auth.api;

import com.example.logmonitor.auth.application.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> authentication(AuthenticationException exception) {
        HttpStatus status = exception.getKind() == AuthenticationException.Kind.RATE_LIMITED
            ? HttpStatus.TOO_MANY_REQUESTS
            : HttpStatus.UNAUTHORIZED;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
        }
        return response.body(error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation() {
        return ResponseEntity.unprocessableEntity()
            .body(error("VALIDATION_ERROR", "Request validation failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformed() {
        return ResponseEntity.badRequest()
            .body(error("MALFORMED_REQUEST", "Request body is malformed"));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
            "success", false,
            "error", Map.of("code", code, "message", message)
        );
    }
}
