package com.example.logmonitor.alerting.api;

import com.example.logmonitor.alerting.application.AlertService;
import com.example.logmonitor.alerting.domain.AlertOccurrence;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertOccurrence> getAlerts(@PathVariable String projectId) {
        return alertService.getAlerts(projectId);
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<AlertOccurrence> getAlert(@PathVariable String projectId, @PathVariable String alertId) {
        return alertService.getAlert(alertId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertOccurrence> acknowledgeAlert(@PathVariable String projectId, @PathVariable String alertId) {
        return alertService.acknowledgeAlert(alertId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{alertId}/retry-notification")
    public ResponseEntity<AlertOccurrence> retryNotification(@PathVariable String projectId, @PathVariable String alertId) {
        return alertService.retryNotification(alertId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
