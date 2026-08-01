package com.example.logmonitor.auth;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.apikey.domain.ApiKeyRepository;
import com.example.logmonitor.alerting.domain.AlertOccurrence;
import com.example.logmonitor.alerting.domain.AlertOccurrenceRepository;
import com.example.logmonitor.alerting.domain.AlertRule;
import com.example.logmonitor.alerting.domain.AlertRuleRepository;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.persistence.LogEventDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
class Phase9SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertOccurrenceRepository alertOccurrenceRepository;

    @Autowired
    private JwtService jwtService;

    private String userToken;
    private String viewerToken;
    private String userId;
    private String viewerId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        membershipRepository.deleteAll();
        apiKeyRepository.deleteAll();
        alertRuleRepository.deleteAll();
        alertOccurrenceRepository.deleteAll();

        User user = userRepository.save(new User(null, "operator_user", "op@example.com", "hash", "org1"));
        userId = user.getId();
        membershipRepository.save(new ProjectMembership(userId, "project-a", Role.PROJECT_OPERATOR));
        userToken = jwtService.generateToken(userId, "operator_user", "org1");

        User viewer = userRepository.save(new User(null, "viewer_user", "viewer@example.com", "hash", "org1"));
        viewerId = viewer.getId();
        membershipRepository.save(new ProjectMembership(viewerId, "project-a", Role.VIEWER));
        viewerToken = jwtService.generateToken(viewerId, "viewer_user", "org1");
    }

    @Test
    void allowsMemberToAccessOwnProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/project-a/logs")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsMemberAccessingForeignProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/foreign-project/logs")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void doesNotExposeForeignLogThroughProjectScopedDetail() throws Exception {
        LogEventDocument foreignLog = new LogEventDocument(
            "foreign-event", Instant.now(), "ERROR", "foreign-service", "prod",
            "FOREIGN_FAILURE", "foreign project event", null, null, null, null, null,
            Instant.now(), Instant.now().plusSeconds(3600), "org-2", "foreign-project", "foreign-key", "FOREIGN_FAILURE"
        );
        String foreignLogId = mongoTemplate.save(foreignLog).getId();

        mockMvc.perform(get("/api/v1/projects/project-a/logs/" + foreignLogId)
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMemberAccessingForeignProjectAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/projects/foreign-project/analytics/summary")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void doesNotExposeForeignProjectNestedAlertResources() throws Exception {
        AlertRule foreignRule = new AlertRule();
        foreignRule.setName("Foreign rule");
        foreignRule.setProjectId("foreign-project");
        String foreignRuleId = alertRuleRepository.save(foreignRule).getId();

        AlertOccurrence foreignOccurrence = new AlertOccurrence();
        foreignOccurrence.setRuleId(foreignRuleId);
        foreignOccurrence.setRuleName("Foreign rule");
        foreignOccurrence.setProjectId("foreign-project");
        String foreignOccurrenceId = alertOccurrenceRepository.save(foreignOccurrence).getId();

        mockMvc.perform(get("/api/v1/projects/project-a/alert-rules/" + foreignRuleId)
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/project-a/alerts/" + foreignOccurrenceId)
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedProjectAccess() throws Exception {
        mockMvc.perform(get("/api/v1/projects/project-a/logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsSystemStatusFromAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/v1/system/ingestion-status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsViewerAttemptingToMutateResource() throws Exception {
        String ruleJson = """
            {
                "name": "Test Rule",
                "threshold": 10,
                "windowSeconds": 60,
                "cooldownSeconds": 300
            }
            """;

        mockMvc.perform(post("/api/v1/projects/project-a/alert-rules")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleJson))
            .andExpect(status().isForbidden());
    }

    @Test
    void validatesValidAndRevokedApiKeysOnIngestion() throws Exception {
        var createResult = apiKeyService.createApiKey("project-a", "Ingestion Key");
        String rawKey = createResult.rawApiKey();

        String payload = """
            {
                "projectId": "project-a",
                "level": "INFO",
                "service": "auth-service",
                "environment": "production",
                "eventType": "USER_LOGIN",
                "message": "Security test log event"
            }
            """;

        // Valid key -> 202 Accepted
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted());

        // Revoke key
        apiKeyService.revokeApiKey(createResult.apiKey().getId());

        // Revoked key -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
    }
}
