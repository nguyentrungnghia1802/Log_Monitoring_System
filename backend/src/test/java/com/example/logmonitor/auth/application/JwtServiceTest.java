package com.example.logmonitor.auth.application;

import com.example.logmonitor.auth.domain.AuthSessionRepository;
import com.example.logmonitor.auth.domain.User;
import com.example.logmonitor.auth.domain.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String SECRET = "A-test-signing-secret-that-is-longer-than-thirty-two-bytes";

    @Test
    void rejectsExpiredAccessToken() {
        UserRepository users = mock(UserRepository.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        User user = new User("user-1", "admin", "admin@example.test", "hash", "org-1");
        when(users.findById("user-1")).thenReturn(Optional.of(user));
        JwtService service = new JwtService(SECRET, -1, users, sessions);

        String token = service.generateToken("user-1", "admin", "org-1");

        assertTrue(service.validateAndExtractPrincipal(token).isEmpty());
    }
}
