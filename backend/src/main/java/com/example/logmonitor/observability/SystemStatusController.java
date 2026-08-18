package com.example.logmonitor.observability;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final IngestionQueue ingestionQueue;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final HealthEndpoint healthEndpoint;
    private final int queueCapacity;
    private final int workerCount;
    private final int batchMaxSize;

    public SystemStatusController(
        IngestionQueue ingestionQueue,
        UserRepository userRepository,
        MeterRegistry meterRegistry,
        HealthEndpoint healthEndpoint,
        @Value("${ingestion.queue.capacity:50000}") int queueCapacity,
        @Value("${ingestion.workers:4}") int workerCount,
        @Value("${ingestion.batch.max-size:500}") int batchMaxSize
    ) {
        this.ingestionQueue = ingestionQueue;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
        this.healthEndpoint = healthEndpoint;
        this.queueCapacity = queueCapacity;
        this.workerCount = workerCount;
        this.batchMaxSize = batchMaxSize;
    }

    @GetMapping("/ingestion-status")
    public Map<String, Object> ingestionStatus() {
        return Map.of(
            "queueDepth", ingestionQueue.size(),
            "queueCapacity", queueCapacity,
            "workerCount", workerCount,
            "batchMaxSize", batchMaxSize,
            "acceptedCount", ingestionQueue.acceptedCount(),
            "rejectedCount", ingestionQueue.rejectedCount(),
            "status", "ready"
        );
    }

    @GetMapping("/health-dashboard")
    public ResponseEntity<?> healthDashboard(Authentication authentication) {
        if (!isPlatformOperator(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "error", Map.of("code", "FORBIDDEN", "message", "Platform operator access required")
            ));
        }

        return ResponseEntity.ok(new HealthDashboardResponse(
            Instant.now(),
            readinessSnapshot(),
            ingestionSnapshot(),
            persistenceSnapshot(),
            liveTailSnapshot(),
            alertSnapshot(),
            runtimeSnapshot()
        ));
    }

    private boolean isPlatformOperator(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof JwtService.UserPrincipal principal)) {
            return false;
        }
        return userRepository.findById(principal.userId())
            .filter(user -> user.isActive())
            .filter(user -> principal.organizationId() != null
                && principal.organizationId().equals(user.getOrganizationId()))
            .map(user -> user.getOrganizationRole() == Role.ORGANIZATION_ADMIN)
            .orElse(false);
    }

    private ReadinessSnapshot readinessSnapshot() {
        HealthComponent readiness = healthEndpoint.healthForPath("readiness");
        Map<String, String> dependencies = new LinkedHashMap<>();
        if (readiness instanceof CompositeHealth composite) {
            composite.getComponents().forEach((name, component) ->
                dependencies.put(name, component.getStatus().getCode()));
        }
        return new ReadinessSnapshot(readiness.getStatus().getCode(), dependencies);
    }

    private IngestionSnapshot ingestionSnapshot() {
        double accepted = counter("ingestion.accepted");
        return new IngestionSnapshot(
            counter("ingestion.received"),
            accepted,
            counter("ingestion.rejected.validation"),
            counter("ingestion.rejected.backpressure"),
            counter("ingestion.rejected.shutdown"),
            ingestionQueue.size(),
            queueCapacity,
            queueCapacity == 0 ? 0 : ingestionQueue.size() * 100.0 / queueCapacity,
            gauge("ingestion.worker.active"),
            workerCount,
            summaryMean("ingestion.batch.size"),
            summaryMax("ingestion.batch.size")
        );
    }

    private PersistenceSnapshot persistenceSnapshot() {
        Timer duration = meterRegistry.find("ingestion.persistence.duration").timer();
        return new PersistenceSnapshot(
            counter("ingestion.persistence.events.saved"),
            counter("ingestion.persistence.events.failed"),
            counter("ingestion.persistence.retries"),
            counter("ingestion.persistence.failures"),
            duration == null ? 0 : duration.mean(TimeUnit.MILLISECONDS),
            duration == null ? 0 : duration.max(TimeUnit.MILLISECONDS)
        );
    }

    private LiveTailSnapshot liveTailSnapshot() {
        return new LiveTailSnapshot(
            gauge("livetail.sessions.active"),
            gauge("livetail.subscriptions.active"),
            counter("livetail.events.sent"),
            counter("livetail.events.dropped"),
            counter("livetail.authorization.failures")
        );
    }

    private AlertSnapshot alertSnapshot() {
        return new AlertSnapshot(
            counter("alert.evaluations"),
            counter("alert.triggered"),
            counter("alert.delivery.success"),
            counter("alert.delivery.failure"),
            counter("alert.delivery.retry")
        );
    }

    private RuntimeSnapshot runtimeSnapshot() {
        return new RuntimeSnapshot(
            gauge("jvm.memory.used", "area", "heap"),
            gauge("jvm.memory.max", "area", "heap"),
            timerCount("jvm.gc.pause"),
            gauge("jvm.threads.live"),
            gauge("process.cpu.usage"),
            gauge("system.cpu.usage")
        );
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    private double gauge(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private double gauge(String name, String tagKey, String tagValue) {
        var gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private double summaryMean(String name) {
        DistributionSummary summary = meterRegistry.find(name).summary();
        return summary == null ? 0 : summary.mean();
    }

    private double summaryMax(String name) {
        DistributionSummary summary = meterRegistry.find(name).summary();
        return summary == null ? 0 : summary.max();
    }

    private long timerCount(String name) {
        Timer timer = meterRegistry.find(name).timer();
        return timer == null ? 0 : timer.count();
    }

    public record HealthDashboardResponse(
        Instant generatedAt,
        ReadinessSnapshot readiness,
        IngestionSnapshot ingestion,
        PersistenceSnapshot persistence,
        LiveTailSnapshot liveTail,
        AlertSnapshot alerts,
        RuntimeSnapshot runtime
    ) { }

    public record ReadinessSnapshot(String status, Map<String, String> dependencies) { }

    public record IngestionSnapshot(
        double receivedTotal,
        double acceptedTotal,
        double validationRejectedTotal,
        double backpressureRejectedTotal,
        double shutdownRejectedTotal,
        int queueDepth,
        int queueCapacity,
        double queueUtilizationPercent,
        double activeWorkers,
        int workerCapacity,
        double averageBatchSize,
        double maxBatchSize
    ) { }

    public record PersistenceSnapshot(
        double eventsSavedTotal,
        double eventsFailedTotal,
        double retriesTotal,
        double failuresTotal,
        double averageDurationMs,
        double maxDurationMs
    ) { }

    public record LiveTailSnapshot(
        double activeSessions,
        double activeSubscriptions,
        double eventsSentTotal,
        double eventsDroppedTotal,
        double authorizationFailuresTotal
    ) { }

    public record AlertSnapshot(
        double evaluationsTotal,
        double triggeredTotal,
        double deliverySuccessTotal,
        double deliveryFailureTotal,
        double deliveryRetryTotal
    ) { }

    public record RuntimeSnapshot(
        double heapUsedBytes,
        double heapMaxBytes,
        long gcPauseCount,
        double liveThreads,
        double processCpuUsage,
        double systemCpuUsage
    ) { }
}
