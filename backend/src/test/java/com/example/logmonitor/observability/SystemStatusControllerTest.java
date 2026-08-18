package com.example.logmonitor.observability;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.ingestion.infrastructure.IngestionQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemStatusControllerTest {

    @Test
    void onlyOrganizationAdministratorsReceivePlatformSnapshot() {
        IngestionQueue queue = mock(IngestionQueue.class);
        UserRepository userRepository = mock(UserRepository.class);
        HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
        when(healthEndpoint.healthForPath("readiness")).thenReturn(Health.up().build());
        SystemStatusController controller = new SystemStatusController(
            queue,
            userRepository,
            new SimpleMeterRegistry(),
            healthEndpoint,
            100,
            4,
            50
        );

        User admin = new User("admin-1", "admin", "admin@example.test", "hash", "org-1");
        admin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        User viewer = new User("viewer-1", "viewer", "viewer@example.test", "hash", "org-1");
        viewer.setOrganizationRole(Role.VIEWER);
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("viewer-1")).thenReturn(Optional.of(viewer));

        var adminAuth = new UsernamePasswordAuthenticationToken(
            new JwtService.UserPrincipal("admin-1", "admin", "org-1"), null, List.of()
        );
        var viewerAuth = new UsernamePasswordAuthenticationToken(
            new JwtService.UserPrincipal("viewer-1", "viewer", "org-1"), null, List.of()
        );

        var adminResponse = controller.healthDashboard(adminAuth);
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertInstanceOf(SystemStatusController.HealthDashboardResponse.class, adminResponse.getBody());

        var viewerResponse = controller.healthDashboard(viewerAuth);
        assertEquals(HttpStatus.FORBIDDEN, viewerResponse.getStatusCode());
        assertTrue(viewerResponse.getBody().toString().contains("Platform operator access required"));
    }
}
