package com.example.logmonitor.livetail.application;

import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.livetail.config.LiveTailProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LiveTailPublisher {

    private static final Logger log = LoggerFactory.getLogger(LiveTailPublisher.class);
    private static final String USER_QUEUE_PREFIX = "/queue/projects/";

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveTailSubscriptionRegistry subscriptionRegistry;
    private final Counter sentEventsCounter;
    private final Counter droppedEventsCounter;

    public LiveTailPublisher(
        SimpMessagingTemplate messagingTemplate,
        LiveTailSubscriptionRegistry subscriptionRegistry,
        LiveTailProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.messagingTemplate = messagingTemplate;
        this.subscriptionRegistry = subscriptionRegistry;
        this.messagingTemplate.setSendTimeout(properties.getBrokerSendTimeoutMs());
        this.sentEventsCounter = Counter.builder("livetail.events.sent")
            .description("Live-tail events accepted by the outbound broker channel")
            .register(meterRegistry);
        this.droppedEventsCounter = Counter.builder("livetail.events.dropped")
            .description("Live-tail events dropped when an outbound channel is saturated or unavailable")
            .register(meterRegistry);
    }

    public void publish(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        for (LogEvent event : events) {
            if (event == null || event.projectId() == null) {
                continue;
            }

            List<LiveTailSubscriptionRegistry.Subscription> subscriptions =
                subscriptionRegistry.matchingSubscriptions(event.projectId(), event);
            for (LiveTailSubscriptionRegistry.Subscription subscription : subscriptions) {
                try {
                    Map<String, Object> headers = Map.of(
                        SimpMessageHeaderAccessor.SESSION_ID_HEADER,
                        subscription.sessionId()
                    );
                    messagingTemplate.convertAndSendToUser(
                        subscription.userId(),
                        USER_QUEUE_PREFIX + event.projectId() + "/livetail",
                        event,
                        headers
                    );
                    sentEventsCounter.increment();
                } catch (Exception ex) {
                    droppedEventsCounter.increment();
                    log.warn(
                        "Dropped live-tail event {} for session {}: {}",
                        event.eventId(),
                        subscription.sessionId(),
                        ex.getMessage()
                    );
                }
            }
        }
    }
}
