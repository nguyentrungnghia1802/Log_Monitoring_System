package com.example.logmonitor.ingestion.application;

import com.example.logmonitor.ingestion.api.BatchIngestionRequest;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.domain.LogEvent;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
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

    public IngestionService(
        IngestionQueue ingestionQueue,
        RetentionPolicyResolver retentionPolicyResolver,
        IngestionPayloadSanitizer payloadSanitizer
    ) {
        this.ingestionQueue = ingestionQueue;
        this.retentionPolicyResolver = retentionPolicyResolver;
        this.payloadSanitizer = payloadSanitizer;
    }

    public AdmissionResult accept(IngestionRequest request, String apiKey) {
        request = payloadSanitizer.sanitize(request);
        String requestId = UUID.randomUUID().toString();
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
        boolean accepted = ingestionQueue.offer(event);

        if (!accepted) {
            return new AdmissionResult(false, 0, requestId, "memory_queue", "Ingestion capacity is temporarily unavailable");
        }

        return new AdmissionResult(true, 1, requestId, "memory_queue", "Accepted");
    }

    public AdmissionResult acceptBatch(BatchIngestionRequest request, String apiKey) {
        request = payloadSanitizer.sanitize(request);
        String requestId = UUID.randomUUID().toString();
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

        boolean accepted = ingestionQueue.offerAll(events);
        if (!accepted) {
            return new AdmissionResult(false, 0, requestId, "memory_queue", "Ingestion capacity is temporarily unavailable");
        }

        return new AdmissionResult(true, events.size(), requestId, "memory_queue", "Accepted");
    }

    public record AdmissionResult(boolean accepted, int acceptedCount, String requestId, String admission, String message) {}
}
