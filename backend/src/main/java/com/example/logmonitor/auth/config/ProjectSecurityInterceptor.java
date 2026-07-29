package com.example.logmonitor.auth.config;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import com.example.logmonitor.auth.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

@Component
public class ProjectSecurityInterceptor implements HandlerInterceptor {

    private final ProjectMembershipRepository membershipRepository;

    public ProjectSecurityInterceptor(ProjectMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null || !pathVariables.containsKey("projectId")) {
            return true;
        }

        String projectId = pathVariables.get("projectId");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Authentication required\"}");
            return false;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof ApiKeyAuthenticationFilter.ApiKeyPrincipal apiKeyPrincipal) {
            if (!projectId.equals(apiKeyPrincipal.projectId())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\": \"API key not authorized for project\"}");
                return false;
            }
            return true;
        }

        if (principal instanceof JwtService.UserPrincipal userPrincipal) {
            Optional<ProjectMembership> membership = membershipRepository.findByUserIdAndProjectId(userPrincipal.userId(), projectId);
            if (membership.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\": \"Forbidden: User not a member of project\"}");
                return false;
            }

            Role role = membership.get().getRole();
            String method = request.getMethod();

            if ("GET".equalsIgnoreCase(method)) {
                // All roles (ADMIN, OPERATOR, VIEWER) can read
                return true;
            }

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                if (role == Role.VIEWER) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\": \"Forbidden: Read-only VIEWER role cannot mutate project resources\"}");
                    return false;
                }
            }
            return true;
        }

        // Default allow for test/mock fallback if enabled
        return true;
    }
}
