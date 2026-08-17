package com.example.logmonitor.project.api;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.audit.domain.AuditLog;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
import com.example.logmonitor.persistence.LogEventDocument;
import com.example.logmonitor.project.domain.Project;
import com.example.logmonitor.project.domain.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String operatorToken;
    private Project existingProject;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(new Query(), LogEventDocument.class);
        mongoTemplate.remove(new Query(), AuditLog.class);
        membershipRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        organizationRepository.save(new Organization("org-c2", "acme-c2", "Acme C2"));
        existingProject = new Project(null, "org-c2", "existing-project", "Existing Project");
        existingProject.setEnvironments(List.of("development", "production"));
        existingProject = projectRepository.save(existingProject);

        User admin = new User(null, "c2-admin", "admin@acme-c2.test", "hash", "org-c2");
        admin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        admin = userRepository.save(admin);
        adminToken = jwtService.generateToken(admin.getId(), admin.getUsername(), "org-c2");

        User operator = new User(null, "c2-operator", "operator@acme-c2.test", "hash", "org-c2");
        operator.setOrganizationRole(Role.PROJECT_OPERATOR);
        operator = userRepository.save(operator);
        membershipRepository.save(new ProjectMembership(operator.getId(), existingProject.getId(), Role.PROJECT_OPERATOR));
        operatorToken = jwtService.generateToken(operator.getId(), operator.getUsername(), "org-c2");
    }

    @Test
    void adminCanCreateManageInspectAndDeactivateProject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "key": "line-smart-queue",
                      "name": "LINE Smart Queue Assistant",
                      "environments": ["development", "staging", "production"],
                      "retention": {"defaultDays": 14, "levelOverrides": {"ERROR": 30}}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value("line-smart-queue"))
            .andExpect(jsonPath("$.retention.defaultDays").value(14))
            .andExpect(jsonPath("$.retention.levelOverrides.ERROR").value(30))
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        String projectId = created.get("id").asText();

        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"key":"line-smart-queue","name":"Duplicate Project"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PROJECT_KEY_EXISTS"));

        mongoTemplate.save(new LogEventDocument(
            "event-c2", Instant.now().minusSeconds(30), "ERROR", "queue-service", "production",
            "QUEUE_CREATE_FAILED", "Queue creation failed", null, null, null, null, null,
            Instant.now().minusSeconds(20), Instant.now().plusSeconds(3600), "org-c2", projectId, "key-c2", "fp-c2"
        ));

        mockMvc.perform(patch("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Queue Platform","environments":["production"],"settings":{"owner":"platform"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Queue Platform"))
            .andExpect(jsonPath("$.environments[0]").value("production"))
            .andExpect(jsonPath("$.settings.owner").value("platform"));

        mockMvc.perform(put("/api/v1/projects/" + projectId + "/retention")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"defaultDays":21,"levelOverrides":{}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retention.defaultDays").value(21));

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.services", hasItem("queue-service")))
            .andExpect(jsonPath("$.recentIngestion.eventsLast24Hours").value(1))
            .andExpect(jsonPath("$.recentIngestion.errorEventsLast24Hours").value(1));

        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].key", hasItem("line-smart-queue")));

        mockMvc.perform(delete("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        List<AuditLog> audits = mongoTemplate.find(new Query(), AuditLog.class);
        assertTrue(audits.stream().anyMatch(audit -> "CREATE".equals(audit.getAction()) && projectId.equals(audit.getProjectId())));
        assertTrue(audits.stream().anyMatch(audit -> "DEACTIVATE".equals(audit.getAction()) && projectId.equals(audit.getProjectId())));
    }

    @Test
    void operatorCanListMembershipProjectButCannotManageOrganizationProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(existingProject.getId()));

        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"key":"operator-project","name":"Operator Project"}
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/projects/" + existingProject.getId())
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Should Fail"}
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/projects/" + existingProject.getId() + "/retention")
                .header("Authorization", "Bearer " + operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"defaultDays":30,"levelOverrides":{"ERROR":90}}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void inactiveProjectRejectsValidApiKeyIngestion() throws Exception {
        String rawApiKey = apiKeyService.createApiKey(existingProject.getId(), "c2-ingestion", "org-c2", "c2-admin").rawApiKey();
        existingProject.setActive(false);
        projectRepository.save(existingProject);

        mockMvc.perform(post("/api/v1/ingest/logs")
                .header("X-API-Key", rawApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "level":"ERROR",
                      "service":"queue-service",
                      "environment":"production",
                      "eventType":"INACTIVE_PROJECT",
                      "message":"should be rejected"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROJECT_INACTIVE"));
    }

    @Test
    void projectDetailDoesNotRevealProjectFromAnotherOrganization() throws Exception {
        Project foreign = projectRepository.save(new Project(null, "foreign-org", "foreign-project", "Foreign Project"));

        mockMvc.perform(get("/api/v1/projects/" + foreign.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }
}
