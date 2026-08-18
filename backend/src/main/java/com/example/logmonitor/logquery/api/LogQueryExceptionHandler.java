package com.example.logmonitor.logquery.api;

import com.example.logmonitor.logquery.application.LogQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = LogQueryController.class)
public class LogQueryExceptionHandler {

    @ExceptionHandler(LogQueryException.class)
    public ResponseEntity<Map<String, Object>> handle(LogQueryException exception) {
        HttpStatus status = exception.getKind() == LogQueryException.Kind.BAD_REQUEST
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_ENTITY;
        return error(status, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedParameter() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER", "A log search query parameter is malformed");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(body);
    }
}
