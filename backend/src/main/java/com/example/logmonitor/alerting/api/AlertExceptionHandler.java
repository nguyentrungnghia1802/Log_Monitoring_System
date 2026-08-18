package com.example.logmonitor.alerting.api;

import com.example.logmonitor.alerting.application.AlertOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = {AlertController.class, AlertRuleController.class})
public class AlertExceptionHandler {

    @ExceptionHandler(AlertOperationException.class)
    public ResponseEntity<Map<String, Object>> handleAlertOperation(AlertOperationException exception) {
        HttpStatus status = exception.getKind() == AlertOperationException.Kind.CONFLICT
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_ENTITY;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", Map.of("code", exception.getCode(), "message", exception.getMessage()));
        return ResponseEntity.status(status).body(body);
    }
}
