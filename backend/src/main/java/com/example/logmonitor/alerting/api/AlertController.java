package com.example.logmonitor.alerting.api;

import com.example.logmonitor.alerting.application.AlertService;
import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.auth.application.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        return alertService.getAlert(projectId, alertId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<AlertOccurrence> acknowledgeAlert(
        @PathVariable String projectId,
        @PathVariable String alertId,
        Authentication authentication
    ) {
        JwtService.UserPrincipal principal = principal(authentication);
        return alertService.acknowledgeAlert(projectId, alertId, principal.username(), principal.organizationId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{alertId}/retry-notification")
    public ResponseEntity<AlertOccurrence> retryNotification(
        @PathVariable String projectId,
        @PathVariable String alertId,
        Authentication authentication
    ) {
        JwtService.UserPrincipal principal = principal(authentication);
        return alertService.retryNotification(projectId, alertId, principal.username(), principal.organizationId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private JwtService.UserPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.UserPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("Authenticated management principal required");
    }
}
