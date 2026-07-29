package com.example.logmonitor.alerting.application;

import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.alerting.domain.AlertOccurrenceRepository;
import com.example.logmonitor.alerting.domain.AlertRule;
import com.example.logmonitor.alerting.domain.AlertRuleRepository;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import com.example.logmonitor.persistence.LogEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    private AlertRuleRepository ruleRepository;
    private AlertOccurrenceRepository occurrenceRepository;
    private MongoTemplate mongoTemplate;
    private AlertNotificationSender notificationSender;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(AlertRuleRepository.class);
        occurrenceRepository = mock(AlertOccurrenceRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        notificationSender = mock(AlertNotificationSender.class);

        alertService = new AlertService(ruleRepository, occurrenceRepository, mongoTemplate, notificationSender);
    }

    @Test
    void triggersAlertWhenThresholdExceededAndNotInCooldown() {
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setName("Error Spike");
        rule.setProjectId("demo-project");
        rule.setEnabled(true);
        rule.setThreshold(5);
        rule.setWindowSeconds(60);
        rule.setCooldownSeconds(300);

        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));
        when(mongoTemplate.count(any(Query.class), eq(LogEventDocument.class))).thenReturn(10L);

        AlertOccurrence occurrence = new AlertOccurrence();
        occurrence.setId("occ-1");
        occurrence.setRuleId("rule-1");
        occurrence.setProjectId("demo-project");
        when(occurrenceRepository.save(any(AlertOccurrence.class))).thenReturn(occurrence);
        when(notificationSender.send(any())).thenReturn(new AlertNotificationSender.NotificationResult(true, "mock", null));

        alertService.evaluateRulesForProject("demo-project");

        verify(occurrenceRepository, times(2)).save(any());
        verify(ruleRepository).save(rule);
        assertNotNull(rule.getCooldownUntil());
    }

    @Test
    void suppressesAlertWhenInCooldown() {
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setProjectId("demo-project");
        rule.setEnabled(true);
        rule.setCooldownUntil(Instant.now().plusSeconds(200));

        when(ruleRepository.findByProjectIdAndEnabled("demo-project", true)).thenReturn(List.of(rule));

        alertService.evaluateRulesForProject("demo-project");

        verifyNoInteractions(mongoTemplate);
        verifyNoInteractions(occurrenceRepository);
    }
}
