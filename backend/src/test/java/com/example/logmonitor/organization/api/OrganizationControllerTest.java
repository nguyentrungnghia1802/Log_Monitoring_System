package com.example.logmonitor.organization.api;

import com.example.logmonitor.audit.domain.AuditLog;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.example.logmonitor.organization.domain.Organization;
import com.example.logmonitor.organization.domain.OrganizationRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
        mongoTemplate.remove(new Query(), AuditLog.class);

        organizationRepository.save(new Organization("org-c1", "acme-c1", "Acme C1"));

        User admin = new User(null, "c1-admin", "admin@acme.test", "hash", "org-c1");
        admin.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        admin = userRepository.save(admin);
        adminToken = jwtService.generateToken(admin.getId(), admin.getUsername(), "org-c1");

        User member = new User(null, "c1-member", "member@acme.test", "hash", "org-c1");
        member.setOrganizationRole(Role.VIEWER);
        member = userRepository.save(member);
        memberToken = jwtService.generateToken(member.getId(), member.getUsername(), "org-c1");
    }

    @Test
    void adminCanReadUpdateAndListOrganizationWithoutPasswordLeakage() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/current")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Acme C1"))
            .andExpect(jsonPath("$.memberCount").value(2));

        mockMvc.perform(patch("/api/v1/organizations/current")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme Platform\",\"settings\":{\"timezone\":\"UTC\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Acme Platform"))
            .andExpect(jsonPath("$.settings.timezone").value("UTC"));

        mockMvc.perform(get("/api/v1/organizations/current/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCanCreateChangeDisableAndRemoveMemberWithAudits() throws Exception {
        String createBody = """
            {
              "username": "created-user",
              "email": "created@acme.test",
              "password": "long-enough-password",
              "role": "PROJECT_OPERATOR"
            }
            """;
        String createResponse = mockMvc.perform(post("/api/v1/organizations/current/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("created-user"))
            .andExpect(jsonPath("$.role").value("PROJECT_OPERATOR"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        String userId = created.get("id").asText();

        mockMvc.perform(patch("/api/v1/organizations/current/users/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"VIEWER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("VIEWER"));

        mockMvc.perform(patch("/api/v1/organizations/current/users/" + userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"created-user\",\"password\":\"long-enough-password\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/organizations/current/users/" + userId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        List<AuditLog> audits = mongoTemplate.find(new Query(), AuditLog.class);
        org.junit.jupiter.api.Assertions.assertTrue(audits.stream().anyMatch(audit -> "CREATE".equals(audit.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(audits.stream().anyMatch(audit -> "DISABLED".equals(audit.getAction())));
        org.junit.jupiter.api.Assertions.assertTrue(audits.stream().anyMatch(audit -> "REMOVED".equals(audit.getAction())));
    }

    @Test
    void keepsFinalOrganizationAdminAndDeniesMemberWrites() throws Exception {
        User admin = userRepository.findByUsername("c1-admin").orElseThrow();

        mockMvc.perform(patch("/api/v1/organizations/current/users/" + admin.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"VIEWER\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("FINAL_ORGANIZATION_ADMIN"));

        mockMvc.perform(patch("/api/v1/organizations/current")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Should Fail\"}"))
            .andExpect(status().isForbidden());
    }
}
