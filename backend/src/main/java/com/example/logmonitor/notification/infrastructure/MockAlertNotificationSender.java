package com.example.logmonitor.notification.infrastructure;

import com.example.logmonitor.notification.domain.AlertNotification;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "alert.notification.mode", havingValue = "mock", matchIfMissing = true)
public class MockAlertNotificationSender implements AlertNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockAlertNotificationSender.class);

    @Override
    public NotificationResult send(AlertNotification notification) {
        log.info("[MOCK NOTIFICATION] Alert Triggered: rule='{}', project='{}', count={}/{}, triggeredAt={}",
            notification.ruleName(), notification.projectId(), notification.observedValue(), notification.threshold(), notification.triggeredAt());
        return new NotificationResult(true, "mock", null);
    }
}
