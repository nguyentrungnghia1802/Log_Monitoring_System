package com.example.logmonitor.alerting.application;

import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.alerting.domain.AlertOccurrenceRepository;
import com.example.logmonitor.alerting.domain.AlertRule;
import com.example.logmonitor.alerting.domain.AlertRuleRepository;
import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.common.security.RedactionProperties;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.lifecycle.GracefulShutdownCoordinator;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import com.example.logmonitor.persistence.LogEventDocument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    private AlertRuleRepository ruleRepository;
    private AlertOccurrenceRepository occurrenceRepository;
    private MongoTemplate mongoTemplate;
    private AlertNotificationSender notificationSender;
    private AuditService auditService;
    private GracefulShutdownCoordinator shutdownCoordinator;
    private SimpleMeterRegistry meterRegistry;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(AlertRuleRepository.class);
        occurrenceRepository = mock(AlertOccurrenceRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        notificationSender = mock(AlertNotificationSender.class);
        auditService = mock(AuditService.class);
        shutdownCoordinator = mock(GracefulShutdownCoordinator.class);
        meterRegistry = new SimpleMeterRegistry();
        when(shutdownCoordinator.isAcceptingTraffic()).thenReturn(true);
        when(occurrenceRepository.save(any(AlertOccurrence.class))).thenAnswer(invocation -> {
            AlertOccurrence occurrence = invocation.getArgument(0);
            if (occurrence.getId() == null) occurrence.setId("occ-1");
            return occurrence;
        });
        alertService = new AlertService(ruleRepository, occurrenceRepository, mongoTemplate, notificationSender,
            auditService, new SensitiveDataRedactor(new RedactionProperties()), shutdownCoordinator,
            meterRegistry);
    }

    @Test
    void triggersAlertWhenThresholdReachedAndNotInCooldown() {
        AlertRule rule = validRule();
        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));
        when(mongoTemplate.count(any(Query.class), eq(LogEventDocument.class))).thenReturn(5L);
        when(notificationSender.send(any()))
            .thenReturn(new AlertNotificationSender.NotificationResult(true, "mock", null));

        alertService.evaluateRulesForProject("demo-project");

        verify(occurrenceRepository, times(2)).save(any());
        verify(ruleRepository).save(rule);
        assertNotNull(rule.getCooldownUntil());
        assertEquals(1.0, meterRegistry.counter("alert.evaluations").count());
        assertEquals(1.0, meterRegistry.counter("alert.triggered").count());
        assertEquals(1.0, meterRegistry.counter("alert.delivery.success").count());
    }

    @Test
    void doesNotTriggerAlertBelowThreshold() {
        AlertRule rule = validRule();
        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));
        when(mongoTemplate.count(any(Query.class), eq(LogEventDocument.class))).thenReturn(4L);

        alertService.evaluateRulesForProject("demo-project");

        verify(mongoTemplate).count(any(Query.class), eq(LogEventDocument.class));
        verifyNoInteractions(occurrenceRepository, notificationSender);
        verify(ruleRepository, never()).save(any(AlertRule.class));
        assertEquals(0.0, meterRegistry.counter("alert.triggered").count());
    }

    @Test
    void suppressesAlertWhenInCooldown() {
        AlertRule rule = validRule();
        rule.setCooldownUntil(Instant.now().plusSeconds(200));
        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));

        alertService.evaluateRulesForProject("demo-project");

        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(occurrenceRepository);
    }

    @Test
    void evaluatesAgainWhenCooldownHasExpired() {
        AlertRule rule = validRule();
        rule.setCooldownUntil(Instant.now().minusSeconds(1));
        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));
        when(mongoTemplate.count(any(Query.class), eq(LogEventDocument.class))).thenReturn(5L);
        when(notificationSender.send(any()))
            .thenReturn(new AlertNotificationSender.NotificationResult(true, "mock", null));

        alertService.evaluateRulesForProject("demo-project");

        verify(occurrenceRepository, times(2)).save(any());
        verify(ruleRepository).save(rule);
        assertEquals(1.0, meterRegistry.counter("alert.triggered").count());
    }

    @Test
    void validatesAndNormalizesRuleFiltersAndRanges() {
        AlertRule rule = validRule();
        rule.setName("  Error Spike  ");
        rule.setLevels(List.of("error", "ERROR"));
        rule.setEventTypes(List.of("QUEUE_FAILED", "QUEUE_FAILED"));
        when(ruleRepository.save(rule)).thenReturn(rule);

        AlertRule saved = alertService.createRule("demo-project", rule);

        assertEquals("Error Spike", saved.getName());
        assertEquals(List.of("ERROR"), saved.getLevels());
        assertEquals(List.of("QUEUE_FAILED"), saved.getEventTypes());
        verify(ruleRepository).findByProjectIdAndNameIgnoreCase("demo-project", "Error Spike");
        verify(ruleRepository).save(rule);

        rule.setWindowSeconds(9);
        AlertOperationException exception = assertThrows(AlertOperationException.class,
            () -> alertService.createRule("demo-project", rule));
        assertEquals("INVALID_ALERT_WINDOW", exception.getCode());
    }

    @Test
    void rejectsUnknownLevelsAndDuplicateNamesWithinProject() {
        AlertRule invalid = validRule();
        invalid.setLevels(List.of("SEVERE"));
        assertEquals("INVALID_ALERT_LEVEL", assertThrows(AlertOperationException.class,
            () -> alertService.createRule("demo-project", invalid)).getCode());

        AlertRule duplicate = validRule();
        when(ruleRepository.findByProjectIdAndNameIgnoreCase("demo-project", duplicate.getName()))
            .thenReturn(Optional.of(validRule()));
        AlertOperationException conflict = assertThrows(AlertOperationException.class,
            () -> alertService.createRule("demo-project", duplicate));
        assertEquals(AlertOperationException.Kind.CONFLICT, conflict.getKind());
        assertEquals("ALERT_RULE_NAME_CONFLICT", conflict.getCode());
    }

    @Test
    void acknowledgementRecordsActorTimeAndAuditOnce() {
        AlertOccurrence occurrence = occurrence();
        when(occurrenceRepository.findByIdAndProjectId("occ-1", "demo-project"))
            .thenReturn(Optional.of(occurrence));

        AlertOccurrence acknowledged = alertService
            .acknowledgeAlert("demo-project", "occ-1", "operator@example.com", "org-1")
            .orElseThrow();

        assertEquals("ACKNOWLEDGED", acknowledged.getStatus());
        assertEquals("operator@example.com", acknowledged.getAcknowledgedBy());
        assertNotNull(acknowledged.getAcknowledgedAt());
        verify(auditService).logAction("operator@example.com", "org-1", "demo-project", "ACKNOWLEDGE",
            "ALERT_OCCURRENCE", "occ-1", "Alert occurrence acknowledged");

        alertService.acknowledgeAlert("demo-project", "occ-1", "other@example.com", "org-1");
        verify(occurrenceRepository, times(1)).save(occurrence);
        verifyNoMoreInteractions(auditService);
    }

    @Test
    void retryUpdatesSameOccurrenceWithSanitizedAttemptHistoryAndAudit() {
        AlertOccurrence occurrence = occurrence();
        AlertRule rule = validRule();
        when(occurrenceRepository.findByIdAndProjectId("occ-1", "demo-project"))
            .thenReturn(Optional.of(occurrence));
        when(ruleRepository.findByIdAndProjectId("rule-1", "demo-project")).thenReturn(Optional.of(rule));
        when(notificationSender.send(any())).thenReturn(new AlertNotificationSender.NotificationResult(
            false, "telegram", "bot_token=super-secret\nupstream denied"));

        AlertOccurrence retried = alertService
            .retryNotification("demo-project", "occ-1", "operator@example.com", "org-1")
            .orElseThrow();

        assertSame(occurrence, retried);
        assertEquals(1, retried.getAttemptCount());
        assertEquals("FAILED", retried.getDeliveryStatus());
        assertFalse(retried.getLastError().contains("super-secret"));
        assertEquals(1, retried.getDeliveryAttempts().size());
        assertEquals("telegram", retried.getDeliveryAttempts().get(0).provider());
        assertEquals("FAILED", retried.getDeliveryAttempts().get(0).status());
        verify(occurrenceRepository, times(1)).save(same(occurrence));
        verify(auditService).logAction("operator@example.com", "org-1", "demo-project", "RETRY_NOTIFICATION",
            "ALERT_OCCURRENCE", "occ-1", "Alert notification delivery retried");
        assertEquals(1.0, meterRegistry.counter("alert.delivery.retry").count());
        assertEquals(1.0, meterRegistry.counter("alert.delivery.failure").count());
    }

    @Test
    void doesNotStartNotificationDeliveryAfterShutdownBegins() {
        AlertOccurrence occurrence = occurrence();
        AlertRule rule = validRule();
        when(occurrenceRepository.findByIdAndProjectId("occ-1", "demo-project"))
            .thenReturn(Optional.of(occurrence));
        when(ruleRepository.findByIdAndProjectId("rule-1", "demo-project")).thenReturn(Optional.of(rule));
        when(shutdownCoordinator.isAcceptingTraffic()).thenReturn(false);

        alertService.retryNotification("demo-project", "occ-1", "operator@example.com", "org-1").orElseThrow();

        verifyNoInteractions(notificationSender);
        assertEquals("FAILED", occurrence.getDeliveryStatus());
        assertEquals("shutdown", occurrence.getDeliveryAttempts().get(0).provider());
        assertEquals("Application is shutting down", occurrence.getLastError());
        assertEquals(1.0, meterRegistry.counter("alert.delivery.retry").count());
        assertEquals(1.0, meterRegistry.counter("alert.delivery.failure").count());
    }

    @Test
    void occurrenceDetailAlwaysUsesProjectScopedLookup() {
        alertService.getAlert("project-a", "occ-foreign");
        verify(occurrenceRepository).findByIdAndProjectId("occ-foreign", "project-a");
        verify(occurrenceRepository, never()).findById("occ-foreign");
    }

    private AlertRule validRule() {
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setName("Error Spike");
        rule.setProjectId("demo-project");
        rule.setEnabled(true);
        rule.setLevels(List.of("ERROR"));
        rule.setEventTypes(List.of("QUEUE_FAILED"));
        rule.setThreshold(5);
        rule.setWindowSeconds(60);
        rule.setCooldownSeconds(300);
        return rule;
    }

    private AlertOccurrence occurrence() {
        AlertOccurrence occurrence = new AlertOccurrence();
        occurrence.setId("occ-1");
        occurrence.setRuleId("rule-1");
        occurrence.setRuleName("Error Spike");
        occurrence.setProjectId("demo-project");
        occurrence.setStatus("TRIGGERED");
        occurrence.setDeliveryStatus("FAILED");
        occurrence.setTriggeredAt(Instant.now());
        return occurrence;
    }
}
