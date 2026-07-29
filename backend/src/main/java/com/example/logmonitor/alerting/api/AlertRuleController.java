package com.example.logmonitor.alerting.api;

import com.example.logmonitor.alerting.application.AlertService;
import com.example.logmonitor.alerting.domain.AlertRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/alert-rules")
public class AlertRuleController {

    private final AlertService alertService;

    public AlertRuleController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertRule> getRules(@PathVariable String projectId) {
        return alertService.getRules(projectId);
    }

    @PostMapping
    public ResponseEntity<AlertRule> createRule(@PathVariable String projectId, @RequestBody AlertRule rule) {
        rule.setProjectId(projectId);
        AlertRule created = alertService.createRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<AlertRule> getRule(@PathVariable String projectId, @PathVariable String ruleId) {
        return alertService.getRule(ruleId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{ruleId}")
    public ResponseEntity<AlertRule> updateRule(@PathVariable String projectId, @PathVariable String ruleId, @RequestBody AlertRule updated) {
        return alertService.updateRule(ruleId, updated)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{ruleId}/enable")
    public ResponseEntity<AlertRule> enableRule(@PathVariable String projectId, @PathVariable String ruleId) {
        return alertService.setRuleEnabled(ruleId, true)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{ruleId}/disable")
    public ResponseEntity<AlertRule> disableRule(@PathVariable String projectId, @PathVariable String ruleId) {
        return alertService.setRuleEnabled(ruleId, false)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable String projectId, @PathVariable String ruleId) {
        alertService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
