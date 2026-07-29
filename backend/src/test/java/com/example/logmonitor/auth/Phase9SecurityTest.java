package com.example.logmonitor.auth;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.apikey.domain.ApiKeyRepository;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

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
