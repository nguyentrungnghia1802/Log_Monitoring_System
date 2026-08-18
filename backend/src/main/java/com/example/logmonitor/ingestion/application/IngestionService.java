package com.example.logmonitor.ingestion.application;

import com.example.logmonitor.ingestion.api.BatchIngestionRequest;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import com.example.logmonitor.lifecycle.GracefulShutdownCoordinator;
import com.example.logmonitor.project.domain.RetentionPolicyResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionService {

    private final IngestionQueue ingestionQueue;
    private final RetentionPolicyResolver retentionPolicyResolver;
    private final IngestionPayloadSanitizer payloadSanitizer;
    private final GracefulShutdownCoordinator shutdownCoordinator;

    public IngestionService(
        IngestionQueue ingestionQueue,
        RetentionPolicyResolver retentionPolicyResolver,
        IngestionPayloadSanitizer payloadSanitizer,
        GracefulShutdownCoordinator shutdownCoordinator
    ) {
        this.ingestionQueue = ingestionQueue;
        this.retentionPolicyResolver = retentionPolicyResolver;
        this.payloadSanitizer = payloadSanitizer;
        this.shutdownCoordinator = shutdownCoordinator;
    }

    public AdmissionResult accept(IngestionRequest request, String apiKey) {
        String requestId = UUID.randomUUID().toString();
        if (!shutdownCoordinator.isAcceptingTraffic()) {
            return rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
        }
        request = payloadSanitizer.sanitize(request);
        String projectId = "demo-project";
        String organizationId = "demo-org";
        String apiKeyId = "demo-key";

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.example.logmonitor.auth.config.ApiKeyAuthenticationFilter.ApiKeyPrincipal keyPrincipal) {
            projectId = keyPrincipal.projectId();
            apiKeyId = keyPrincipal.apiKeyId();
            if (keyPrincipal.organizationId() != null && !keyPrincipal.organizationId().isBlank()) {
                organizationId = keyPrincipal.organizationId();
            }
        }

        long retentionSeconds = retentionPolicyResolver.resolveRetentionSeconds(projectId, request.level());
        LogEvent event = LogEvent.of(request, organizationId, projectId, apiKeyId, retentionSeconds);
        boolean accepted;
        boolean shuttingDown;
        synchronized (shutdownCoordinator) {
            if (!shutdownCoordinator.isAcceptingTraffic()) {
                return rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
            }
            accepted = ingestionQueue.offer(event);
            shuttingDown = !shutdownCoordinator.isAcceptingTraffic();
        }

        if (!accepted) {
            return !shuttingDown
                ? rejected(requestId, "INGESTION_BACKPRESSURE", "Ingestion capacity is temporarily unavailable")
                : rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
        }

        return new AdmissionResult(true, 1, requestId, "memory_queue", "Accepted");
    }

    public AdmissionResult acceptBatch(BatchIngestionRequest request, String apiKey) {
        String requestId = UUID.randomUUID().toString();
        if (!shutdownCoordinator.isAcceptingTraffic()) {
            return rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
        }
        request = payloadSanitizer.sanitize(request);
        List<LogEvent> events = new ArrayList<>();
        String projectId = "demo-project";
        String organizationId = "demo-org";
        String apiKeyId = "demo-key";

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.example.logmonitor.auth.config.ApiKeyAuthenticationFilter.ApiKeyPrincipal keyPrincipal) {
            projectId = keyPrincipal.projectId();
            apiKeyId = keyPrincipal.apiKeyId();
            if (keyPrincipal.organizationId() != null && !keyPrincipal.organizationId().isBlank()) {
                organizationId = keyPrincipal.organizationId();
            }
        }

        for (IngestionRequest item : request.events()) {
            long retentionSeconds = retentionPolicyResolver.resolveRetentionSeconds(projectId, item.level());
            events.add(LogEvent.of(item, organizationId, projectId, apiKeyId, retentionSeconds));
        }

        boolean accepted;
        boolean shuttingDown;
        synchronized (shutdownCoordinator) {
            if (!shutdownCoordinator.isAcceptingTraffic()) {
                return rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
            }
            accepted = ingestionQueue.offerAll(events);
            shuttingDown = !shutdownCoordinator.isAcceptingTraffic();
        }
        if (!accepted) {
            return !shuttingDown
                ? rejected(requestId, "INGESTION_BACKPRESSURE", "Ingestion capacity is temporarily unavailable")
                : rejected(requestId, "INGESTION_SHUTTING_DOWN", "Ingestion is stopping for graceful shutdown");
        }

        return new AdmissionResult(true, events.size(), requestId, "memory_queue", "Accepted");
    }

    private AdmissionResult rejected(String requestId, String errorCode, String message) {
        return new AdmissionResult(false, 0, requestId, "memory_queue", message, errorCode);
    }

    public record AdmissionResult(
        boolean accepted,
        int acceptedCount,
        String requestId,
        String admission,
        String message,
        String errorCode
    ) {
        public AdmissionResult(
            boolean accepted,
            int acceptedCount,
            String requestId,
            String admission,
            String message
        ) {
            this(accepted, acceptedCount, requestId, admission, message,
                accepted ? null : "INGESTION_BACKPRESSURE");
        }
    }
}
