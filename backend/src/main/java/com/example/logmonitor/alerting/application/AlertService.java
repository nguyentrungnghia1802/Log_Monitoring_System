package com.example.logmonitor.alerting.application;

import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.alerting.domain.AlertOccurrenceRepository;
import com.example.logmonitor.alerting.domain.AlertRule;
import com.example.logmonitor.alerting.domain.AlertRuleRepository;
import com.example.logmonitor.notification.domain.AlertNotification;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import com.example.logmonitor.persistence.LogEventDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertOccurrenceRepository occurrenceRepository;
    private final MongoTemplate mongoTemplate;
    private final AlertNotificationSender notificationSender;

    public AlertService(
        AlertRuleRepository ruleRepository,
        AlertOccurrenceRepository occurrenceRepository,
        MongoTemplate mongoTemplate,
        AlertNotificationSender notificationSender
    ) {
        this.ruleRepository = ruleRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationSender = notificationSender;
    }

    public List<AlertRule> getRules(String projectId) {
        return ruleRepository.findByProjectId(projectId);
    }

    public AlertRule createRule(String projectId, AlertRule rule) {
        rule.setProjectId(projectId);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    public Optional<AlertRule> getRule(String projectId, String ruleId) {
        return ruleRepository.findByIdAndProjectId(ruleId, projectId);
    }

    public Optional<AlertRule> updateRule(String projectId, String ruleId, AlertRule updated) {
        return ruleRepository.findByIdAndProjectId(ruleId, projectId).map(rule -> {
            if (updated.getName() != null) rule.setName(updated.getName());
            if (updated.getEnvironment() != null) rule.setEnvironment(updated.getEnvironment());
            if (updated.getService() != null) rule.setService(updated.getService());
            if (updated.getLevels() != null) rule.setLevels(updated.getLevels());
            if (updated.getEventTypes() != null) rule.setEventTypes(updated.getEventTypes());
            if (updated.getWindowSeconds() > 0) rule.setWindowSeconds(updated.getWindowSeconds());
            if (updated.getThreshold() > 0) rule.setThreshold(updated.getThreshold());
            if (updated.getCooldownSeconds() > 0) rule.setCooldownSeconds(updated.getCooldownSeconds());
            rule.setUpdatedAt(Instant.now());
            return ruleRepository.save(rule);
        });
    }

    public Optional<AlertRule> setRuleEnabled(String projectId, String ruleId, boolean enabled) {
        return ruleRepository.findByIdAndProjectId(ruleId, projectId).map(rule -> {
            rule.setEnabled(enabled);
            rule.setUpdatedAt(Instant.now());
            return ruleRepository.save(rule);
        });
    }

    public boolean deleteRule(String projectId, String ruleId) {
        return ruleRepository.findByIdAndProjectId(ruleId, projectId)
            .map(rule -> {
                ruleRepository.delete(rule);
                return true;
            })
            .orElse(false);
    }

    public void evaluateRulesForProject(String projectId) {
        List<AlertRule> rules = ruleRepository.findByProjectIdAndEnabled(projectId, true);
        Instant now = Instant.now();

        for (AlertRule rule : rules) {
            if (rule.getCooldownUntil() != null && now.isBefore(rule.getCooldownUntil())) {
                continue;
            }

            Instant windowStart = now.minusSeconds(rule.getWindowSeconds());
            Query query = new Query(Criteria.where("project_id").is(projectId)
                .and("timestamp").gte(windowStart).lte(now));

            if (rule.getEnvironment() != null && !rule.getEnvironment().isBlank()) {
                query.addCriteria(Criteria.where("environment").is(rule.getEnvironment()));
            }
            if (rule.getService() != null && !rule.getService().isBlank()) {
                query.addCriteria(Criteria.where("service").is(rule.getService()));
            }
            if (rule.getLevels() != null && !rule.getLevels().isEmpty()) {
                query.addCriteria(Criteria.where("level").in(rule.getLevels()));
            }
            if (rule.getEventTypes() != null && !rule.getEventTypes().isEmpty()) {
                query.addCriteria(Criteria.where("event_type").in(rule.getEventTypes()));
            }

            long count = mongoTemplate.count(query, LogEventDocument.class);

            if (count >= rule.getThreshold()) {
                triggerAlert(rule, count, windowStart, now);
            }
        }
    }

    private void triggerAlert(AlertRule rule, long observedValue, Instant windowStart, Instant windowEnd) {
        Instant now = Instant.now();

        AlertOccurrence occurrence = new AlertOccurrence();
        occurrence.setRuleId(rule.getId());
        occurrence.setRuleName(rule.getName());
        occurrence.setProjectId(rule.getProjectId());
        occurrence.setTriggeredAt(now);
        occurrence.setWindowStart(windowStart);
        occurrence.setWindowEnd(windowEnd);
        occurrence.setObservedValue(observedValue);
        occurrence.setThreshold(rule.getThreshold());
        occurrence.setStatus("TRIGGERED");
        occurrence.setDeliveryStatus("PENDING");

        AlertOccurrence saved = occurrenceRepository.save(occurrence);

        rule.setCooldownUntil(now.plusSeconds(rule.getCooldownSeconds()));
        ruleRepository.save(rule);

        dispatchNotification(saved, rule);
    }

    public AlertNotificationSender.NotificationResult dispatchNotification(AlertOccurrence occurrence, AlertRule rule) {
        AlertNotification notification = new AlertNotification(
            occurrence.getId(),
            occurrence.getRuleId(),
            occurrence.getRuleName(),
            occurrence.getProjectId(),
            rule != null ? rule.getEnvironment() : null,
            rule != null ? rule.getService() : null,
            occurrence.getObservedValue(),
            occurrence.getThreshold(),
            occurrence.getTriggeredAt()
        );

        occurrence.setAttemptCount(occurrence.getAttemptCount() + 1);
        occurrence.setLastAttemptAt(Instant.now());

        AlertNotificationSender.NotificationResult result = notificationSender.send(notification);

        if (result.success()) {
            occurrence.setDeliveryStatus("DELIVERED");
            occurrence.setLastError(null);
        } else {
            occurrence.setDeliveryStatus("FAILED");
            occurrence.setLastError(result.errorDetails());
        }

        occurrenceRepository.save(occurrence);
        return result;
    }

    public List<AlertOccurrence> getAlerts(String projectId) {
        return occurrenceRepository.findByProjectId(projectId);
    }

    public Optional<AlertOccurrence> getAlert(String projectId, String alertId) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId);
    }

    public Optional<AlertOccurrence> acknowledgeAlert(String projectId, String alertId) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId).map(occ -> {
            occ.setStatus("ACKNOWLEDGED");
            return occurrenceRepository.save(occ);
        });
    }

    public Optional<AlertOccurrence> retryNotification(String projectId, String alertId) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId).map(occ -> {
            AlertRule rule = ruleRepository.findByIdAndProjectId(occ.getRuleId(), projectId).orElse(null);
            dispatchNotification(occ, rule);
            return occ;
        });
    }
}
