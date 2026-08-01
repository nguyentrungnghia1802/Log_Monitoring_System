package com.example.logmonitor.notification.infrastructure;

import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.notification.domain.AlertNotification;
import com.example.logmonitor.notification.domain.AlertNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "alert.notification.mode", havingValue = "telegram")
public class TelegramAlertNotificationSender implements AlertNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlertNotificationSender.class);

    private final String botToken;
    private final String chatId;
    private final RestTemplate restTemplate;
    private final SensitiveDataRedactor redactor;

    public TelegramAlertNotificationSender(
        @Value("${telegram.bot.token:}") String botToken,
        @Value("${telegram.chat.id:}") String chatId,
        SensitiveDataRedactor redactor
    ) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restTemplate = new RestTemplate();
        this.redactor = redactor;
    }

    @Override
    public NotificationResult send(AlertNotification notification) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Telegram bot token or chat ID is unconfigured");
            return new NotificationResult(false, "telegram", "Unconfigured credentials");
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            String text = String.format("""
                🚨 *ALERT TRIGGERED* 🚨
                *Rule:* %s
                *Project:* %s
                *Environment:* %s
                *Service:* %s
                *Observed Count:* %d (Threshold: %d)
                *Triggered At:* %s
                """,
                sanitize(notification.ruleName()),
                sanitize(notification.projectId()),
                sanitize(notification.environment() == null ? "N/A" : notification.environment()),
                sanitize(notification.service() == null ? "N/A" : notification.service()),
                notification.observedValue(),
                notification.threshold(),
                notification.triggeredAt()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", "Markdown"
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);

            log.info("Telegram notification successfully sent for alert: {}", notification.alertId());
            return new NotificationResult(true, "telegram", null);
        } catch (Exception ex) {
            String safeMessage = redactor.redactText(ex.getMessage());
            log.error(
                "Failed to send Telegram alert notification: type={} message={}",
                ex.getClass().getSimpleName(),
                safeMessage
            );
            return new NotificationResult(false, "telegram", safeMessage);
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replace("[", "\\[");
    }
}
