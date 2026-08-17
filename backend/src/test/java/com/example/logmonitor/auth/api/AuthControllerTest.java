package com.example.logmonitor.auth.api;

import com.example.logmonitor.audit.domain.AuditLog;
import com.example.logmonitor.auth.domain.AuthSession;
import com.example.logmonitor.auth.domain.AuthSessionRepository;
import com.example.logmonitor.auth.domain.Role;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "auth.login-burst-capacity=3",
    "auth.login-window-seconds=3600"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        mongoTemplate.remove(new Query(), AuditLog.class);
    }

    @Test
    void loginUsesEmailAndReturnsShortAccessTokenWithHttpOnlyRefreshCookie() throws Exception {
        User user = saveUser("Admin", "Admin@Example.test", "correct-password", true);

        MvcResult result = login("ADMIN@example.test", "correct-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.user.email").value("Admin@Example.test"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("lm_refresh=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
            .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("correct-password"));
        assertTrue(passwordEncoder.matches("correct-password", user.getPasswordHash()));
        assertEquals(1, sessionRepository.count());
    }

    @Test
    void unknownDisabledAndWrongPasswordUseSameGenericFailure() throws Exception {
        saveUser("disabled", "disabled@example.test", "correct-password", false);
        saveUser("active", "active@example.test", "correct-password", true);

        String unknown = failureBody("unknown@example.test", "wrong-password");
        String disabled = failureBody("disabled@example.test", "correct-password");
        String wrong = failureBody("active@example.test", "wrong-password");

        assertEquals(objectMapper.readTree(unknown).get("error"), objectMapper.readTree(disabled).get("error"));
        assertEquals(objectMapper.readTree(unknown).get("error"), objectMapper.readTree(wrong).get("error"));
        assertEquals("INVALID_CREDENTIALS", objectMapper.readTree(unknown).at("/error/code").asText());
    }

    @Test
    void refreshRotatesSessionAndLogoutRevokesReplacement() throws Exception {
        saveUser("operator", "operator@example.test", "correct-password", true);
        MvcResult login = login("operator@example.test", "correct-password")
            .andExpect(status().isOk()).andReturn();
        Cookie firstCookie = login.getResponse().getCookie("lm_refresh");
        String firstAccess = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString(firstCookie.getValue()))))
            .andReturn();
        Cookie secondCookie = refresh.getResponse().getCookie("lm_refresh");
        String secondAccess = objectMapper.readTree(refresh.getResponse().getContentAsString()).get("accessToken").asText();
        assertNotEquals(firstAccess, secondAccess);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("operator"));

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess)
                .cookie(secondCookie))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
            .andExpect(status().isUnauthorized());

        List<AuthSession> sessions = sessionRepository.findAll();
        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().allMatch(session -> session.getRevokedAt() != null));
        List<AuditLog> audits = mongoTemplate.find(new Query(), AuditLog.class);
        assertTrue(audits.stream().anyMatch(audit -> "LOGIN".equals(audit.getAction())));
        assertTrue(audits.stream().anyMatch(audit -> "REFRESH".equals(audit.getAction())));
        assertTrue(audits.stream().anyMatch(audit -> "LOGOUT".equals(audit.getAction())));
    }

    @Test
    void rateLimitsRepeatedAttemptsAndRegisterIsNotPublic() throws Exception {
        String email = "limited@example.test";
        for (int attempt = 0; attempt < 3; attempt++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(email, "wrong-password")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
            .andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedAccessAndRefreshTokens() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.COOKIE, "lm_refresh=malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    private User saveUser(String username, String email, String password, boolean active) {
        User user = new User(null, username, email, passwordEncoder.encode(password), "org-auth-test");
        user.setOrganizationRole(Role.ORGANIZATION_ADMIN);
        user.setActive(active);
        return userRepository.save(user);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginPayload(email, password))));
    }

    private String failureBody(String email, String password) throws Exception {
        return login(email, password).andExpect(status().isUnauthorized()).andReturn()
            .getResponse().getContentAsString();
    }

    private record LoginPayload(String email, String password) {
    }
}
