package com.example.logmonitor.alerting.application;

import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.alerting.domain.AlertOccurrenceRepository;
import com.example.logmonitor.alerting.domain.AlertRule;
import com.example.logmonitor.alerting.domain.AlertRuleRepository;
import com.example.logmonitor.audit.application.AuditService;
import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.lifecycle.GracefulShutdownCoordinator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.example.logmonitor.notification.domain.AlertNotification;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import com.example.logmonitor.persistence.LogEventDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AlertService {

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_FILTER_LENGTH = 100;
    private static final int MAX_FILTER_VALUES = 20;
    private static final long MIN_WINDOW_SECONDS = 10;
    private static final long MAX_WINDOW_SECONDS = 86_400;
    private static final long MAX_THRESHOLD = 1_000_000;
    private static final long MIN_COOLDOWN_SECONDS = 1;
    private static final long MAX_COOLDOWN_SECONDS = 604_800;
    private static final int MAX_PROVIDER_ERROR_LENGTH = 240;
    private static final Set<String> ALLOWED_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");

    private final AlertRuleRepository ruleRepository;
    private final AlertOccurrenceRepository occurrenceRepository;
    private final MongoTemplate mongoTemplate;
    private final AlertNotificationSender notificationSender;
    private final AuditService auditService;
    private final SensitiveDataRedactor redactor;
    private final GracefulShutdownCoordinator shutdownCoordinator;
    private final Counter evaluationCounter;
    private final Counter triggeredCounter;
    private final Counter deliverySuccessCounter;
    private final Counter deliveryFailureCounter;
    private final Counter deliveryRetryCounter;

    public AlertService(
        AlertRuleRepository ruleRepository,
        AlertOccurrenceRepository occurrenceRepository,
        MongoTemplate mongoTemplate,
        AlertNotificationSender notificationSender,
        AuditService auditService,
        SensitiveDataRedactor redactor,
        GracefulShutdownCoordinator shutdownCoordinator,
        MeterRegistry meterRegistry
    ) {
        this.ruleRepository = ruleRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationSender = notificationSender;
        this.auditService = auditService;
        this.redactor = redactor;
        this.shutdownCoordinator = shutdownCoordinator;
        this.evaluationCounter = Counter.builder("alert.evaluations")
            .description("Alert rules evaluated against a project window")
            .register(meterRegistry);
        this.triggeredCounter = Counter.builder("alert.triggered")
            .description("Alert occurrences triggered after threshold evaluation")
            .register(meterRegistry);
        this.deliverySuccessCounter = Counter.builder("alert.delivery.success")
            .description("Alert notifications delivered successfully")
            .register(meterRegistry);
        this.deliveryFailureCounter = Counter.builder("alert.delivery.failure")
            .description("Alert notifications that failed delivery")
            .register(meterRegistry);
        this.deliveryRetryCounter = Counter.builder("alert.delivery.retry")
            .description("Operator-requested alert notification retries")
            .register(meterRegistry);
    }

    public List<AlertRule> getRules(String projectId) {
        return ruleRepository.findByProjectId(projectId);
    }

    public AlertRule createRule(String projectId, AlertRule rule) {
        normalizeAndValidate(rule);
        ensureUniqueName(projectId, rule.getName(), null);
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
            if (updated.getWindowSeconds() < 0 || updated.getThreshold() < 0 || updated.getCooldownSeconds() < 0) {
                throw validation("INVALID_ALERT_RULE_RANGE", "Window, threshold, and cooldown cannot be negative");
            }
            normalizeAndValidate(rule);
            ensureUniqueName(projectId, rule.getName(), ruleId);
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
        evaluationCounter.increment(rules.size());
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
        triggeredCounter.increment();

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

        AlertNotificationSender.NotificationResult result;
        if (!shutdownCoordinator.isAcceptingTraffic()) {
            result = new AlertNotificationSender.NotificationResult(
                false,
                "shutdown",
                "Application is shutting down"
            );
        } else {
            try {
                result = notificationSender.send(notification);
            } catch (RuntimeException exception) {
                result = new AlertNotificationSender.NotificationResult(
                    false,
                    notificationSender.getClass().getSimpleName(),
                    exception.getMessage()
                );
            }
        }

        Instant attemptedAt = occurrence.getLastAttemptAt();
        String provider = sanitizeProvider(result.provider());
        String safeError = result.success() ? null : sanitizeProviderError(result.errorDetails());

        if (result.success()) {
            deliverySuccessCounter.increment();
            occurrence.setDeliveryStatus("DELIVERED");
            occurrence.setLastError(null);
        } else {
            deliveryFailureCounter.increment();
            occurrence.setDeliveryStatus("FAILED");
            occurrence.setLastError(safeError);
        }

        occurrence.addDeliveryAttempt(new AlertOccurrence.DeliveryAttempt(
            occurrence.getAttemptCount(),
            provider,
            attemptedAt,
            result.success() ? "DELIVERED" : "FAILED",
            safeError
        ));

        occurrenceRepository.save(occurrence);
        return new AlertNotificationSender.NotificationResult(result.success(), provider, safeError);
    }

    public List<AlertOccurrence> getAlerts(String projectId) {
        return occurrenceRepository.findByProjectId(projectId);
    }

    public Optional<AlertOccurrence> getAlert(String projectId, String alertId) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId);
    }

    public Optional<AlertOccurrence> acknowledgeAlert(
        String projectId,
        String alertId,
        String actor,
        String organizationId
    ) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId).map(occ -> {
            if (!"ACKNOWLEDGED".equals(occ.getStatus())) {
                occ.setStatus("ACKNOWLEDGED");
                occ.setAcknowledgedAt(Instant.now());
                occ.setAcknowledgedBy(actor);
                occurrenceRepository.save(occ);
                auditService.logAction(actor, organizationId, projectId, "ACKNOWLEDGE", "ALERT_OCCURRENCE",
                    occ.getId(), "Alert occurrence acknowledged");
            }
            return occ;
        });
    }

    public Optional<AlertOccurrence> retryNotification(
        String projectId,
        String alertId,
        String actor,
        String organizationId
    ) {
        return occurrenceRepository.findByIdAndProjectId(alertId, projectId).map(occ -> {
            AlertRule rule = ruleRepository.findByIdAndProjectId(occ.getRuleId(), projectId).orElse(null);
            deliveryRetryCounter.increment();
            dispatchNotification(occ, rule);
            auditService.logAction(actor, organizationId, projectId, "RETRY_NOTIFICATION", "ALERT_OCCURRENCE",
                occ.getId(), "Alert notification delivery retried");
            return occ;
        });
    }

    private void normalizeAndValidate(AlertRule rule) {
        String name = normalizeRequired(rule.getName());
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw validation("INVALID_ALERT_RULE_NAME", "Rule name must contain 1 to 120 characters");
        }
        rule.setName(name);
        rule.setEnvironment(normalizeOptional(rule.getEnvironment(), "environment"));
        rule.setService(normalizeOptional(rule.getService(), "service"));
        rule.setLevels(normalizeLevels(rule.getLevels()));
        rule.setEventTypes(normalizeFilterValues(rule.getEventTypes(), "event type"));

        if (rule.getWindowSeconds() < MIN_WINDOW_SECONDS || rule.getWindowSeconds() > MAX_WINDOW_SECONDS) {
            throw validation("INVALID_ALERT_WINDOW", "Window must be between 10 and 86400 seconds");
        }
        if (rule.getThreshold() < 1 || rule.getThreshold() > MAX_THRESHOLD) {
            throw validation("INVALID_ALERT_THRESHOLD", "Threshold must be between 1 and 1000000 events");
        }
        if (rule.getCooldownSeconds() < MIN_COOLDOWN_SECONDS || rule.getCooldownSeconds() > MAX_COOLDOWN_SECONDS) {
            throw validation("INVALID_ALERT_COOLDOWN", "Cooldown must be between 1 and 604800 seconds");
        }
    }

    private void ensureUniqueName(String projectId, String name, String currentRuleId) {
        ruleRepository.findByProjectIdAndNameIgnoreCase(projectId, name)
            .filter(existing -> currentRuleId == null || !currentRuleId.equals(existing.getId()))
            .ifPresent(existing -> {
                throw new AlertOperationException(AlertOperationException.Kind.CONFLICT,
                    "ALERT_RULE_NAME_CONFLICT", "An alert rule with this name already exists in the project");
            });
    }

    private List<String> normalizeLevels(List<String> levels) {
        List<String> normalized = new ArrayList<>(normalizeFilterValues(levels, "level").stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        if (!ALLOWED_LEVELS.containsAll(normalized)) {
            throw validation("INVALID_ALERT_LEVEL", "Levels must be TRACE, DEBUG, INFO, WARN, ERROR, or FATAL");
        }
        return normalized;
    }

    private List<String> normalizeFilterValues(List<String> values, String label) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_FILTER_VALUES) {
            throw validation("INVALID_ALERT_FILTER", "At most 20 " + label + " values are allowed");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String item = normalizeRequired(value);
            if (item == null || item.length() > MAX_FILTER_LENGTH) {
                throw validation("INVALID_ALERT_FILTER", "Each " + label + " must contain 1 to 100 characters");
            }
            normalized.add(item);
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeOptional(String value, String label) {
        String normalized = normalizeRequired(value);
        if (normalized != null && normalized.length() > MAX_FILTER_LENGTH) {
            throw validation("INVALID_ALERT_FILTER", "Rule " + label + " must contain at most 100 characters");
        }
        return normalized;
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sanitizeProvider(String provider) {
        String safe = redactor.redactText(provider == null ? "unknown" : provider).trim();
        return safe.substring(0, Math.min(safe.length(), MAX_FILTER_LENGTH));
    }

    private String sanitizeProviderError(String error) {
        String safe = redactor.redactText(error == null ? "Notification provider failed" : error)
            .replaceAll("[\\r\\n\\t]+", " ")
            .trim();
        if (safe.isEmpty()) {
            safe = "Notification provider failed";
        }
        return safe.substring(0, Math.min(safe.length(), MAX_PROVIDER_ERROR_LENGTH));
    }

    private AlertOperationException validation(String code, String message) {
        return new AlertOperationException(AlertOperationException.Kind.VALIDATION, code, message);
    }
}
