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
import com.example.logmonitor.project.domain.Project;
import com.example.logmonitor.project.domain.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    private ProjectRepository projectRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertOccurrenceRepository alertOccurrenceRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String viewerToken;
    private String adminToken;
    private String userId;
    private String viewerId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        membershipRepository.deleteAll();
        apiKeyRepository.deleteAll();
        projectRepository.deleteAll();
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

        User admin = userRepository.save(new User(null, "admin_user", "admin@example.com", "hash", "org1"));
        membershipRepository.save(new ProjectMembership(admin.getId(), "project-a", Role.ORGANIZATION_ADMIN));
        adminToken = jwtService.generateToken(admin.getId(), "admin_user", "org1");

        projectRepository.save(new Project("project-a", "org1", "project-a", "Project A"));
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

    @Test
    void organizationAdminCanCreateListRotateAndRevokeWithoutSecretLeakage() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/project-a/api-keys")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"production source\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rawApiKey").isString())
            .andExpect(jsonPath("$.hashedSecret").doesNotExist())
            .andExpect(jsonPath("$.secretLast4").isString())
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String oldRawKey = created.get("rawApiKey").asText();
        String oldKeyId = created.get("id").asText();

        mockMvc.perform(get("/api/v1/projects/project-a/api-keys")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rawApiKey").doesNotExist())
            .andExpect(jsonPath("$[0].hashedSecret").doesNotExist())
            .andExpect(jsonPath("$[0].publicId").isString());

        MvcResult rotateResult = mockMvc.perform(post("/api/v1/projects/project-a/api-keys/" + oldKeyId + "/rotate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rawApiKey").isString())
            .andExpect(jsonPath("$.hashedSecret").doesNotExist())
            .andReturn();
        JsonNode rotated = objectMapper.readTree(rotateResult.getResponse().getContentAsString());
        String newRawKey = rotated.get("rawApiKey").asText();
        String newKeyId = rotated.get("id").asText();
        assertNotEquals(oldRawKey, newRawKey);

        String payload = """
            {
                "projectId": "foreign-project",
                "level": "INFO",
                "service": "api-key-test",
                "environment": "production",
                "eventType": "ROTATION_CHECK",
                "message": "project comes from key scope"
            }
            """;
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", oldRawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", newRawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/v1/projects/project-a/api-keys/" + newKeyId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", newRawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOrganizationAdminCanManageApiKeys() throws Exception {
        mockMvc.perform(get("/api/v1/projects/project-a/api-keys")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects/project-a/api-keys")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"should fail\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/foreign-project/api-keys")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void globalOrganizationAdminCannotManageApiKeysForAnotherOrganization() throws Exception {
        User globalAdmin = new User(null, "global_admin_user", "global-admin@example.com", "hash", "org1");
        globalAdmin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        globalAdmin = userRepository.save(globalAdmin);
        String globalAdminToken = jwtService.generateToken(globalAdmin.getId(), "global_admin_user", "org1");
        projectRepository.save(new Project("foreign-project", "org2", "foreign-project", "Foreign Project"));

        mockMvc.perform(get("/api/v1/projects/foreign-project/api-keys")
                .header("Authorization", "Bearer " + globalAdminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
}
