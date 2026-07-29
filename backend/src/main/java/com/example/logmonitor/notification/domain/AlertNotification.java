package com.example.logmonitor.notification.domain;

import java.time.Instant;

public record AlertNotification(
    String alertId,
    String ruleId,
    String ruleName,
    String projectId,
    String environment,
    String service,
    long observedValue,
    long threshold,
    Instant triggeredAt
) {}
