package com.example.logmonitor.livetail.application;

import com.example.logmonitor.ingestion.domain.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiveTailPublisher {

    private static final Logger log = LoggerFactory.getLogger(LiveTailPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public LiveTailPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(List<LogEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (LogEvent event : events) {
            try {
                String destination = "/topic/projects/" + event.projectId() + "/livetail";
                messagingTemplate.convertAndSend(destination, event);
            } catch (Exception ex) {
                log.warn("Failed to broadcast live tail event: {}", event.eventId(), ex);
            }
        }
    }
}
