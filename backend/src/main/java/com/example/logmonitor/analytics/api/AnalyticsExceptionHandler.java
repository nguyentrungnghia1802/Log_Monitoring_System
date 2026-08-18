package com.example.logmonitor.analytics.api;

import com.example.logmonitor.analytics.application.AnalyticsQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = AnalyticsController.class)
public class AnalyticsExceptionHandler {

    @ExceptionHandler(AnalyticsQueryException.class)
    public ResponseEntity<Map<String, Object>> handle(AnalyticsQueryException exception) {
        HttpStatus status = exception.getKind() == AnalyticsQueryException.Kind.BAD_REQUEST
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_ENTITY;
        return error(status, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedParameter() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_PARAMETER", "An analytics query parameter is malformed");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(body);
    }
}
