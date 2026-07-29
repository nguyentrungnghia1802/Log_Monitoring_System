package com.example.logmonitor.notification.domain;

public interface AlertNotificationSender {
    NotificationResult send(AlertNotification notification);

    record NotificationResult(boolean success, String provider, String errorDetails) {}
}
