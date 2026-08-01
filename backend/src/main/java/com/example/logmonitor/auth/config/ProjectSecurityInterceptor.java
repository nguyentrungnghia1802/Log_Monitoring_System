package com.example.logmonitor.auth.config;

import com.example.logmonitor.auth.application.ProjectAuthorizationService;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.AuthorizationDecision;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.Failure;
import com.example.logmonitor.auth.application.ProjectAuthorizationService.Permission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class ProjectSecurityInterceptor implements HandlerInterceptor {

    private final ProjectAuthorizationService authorizationService;

    public ProjectSecurityInterceptor(ProjectAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null || !pathVariables.containsKey("projectId")) {
            return true;
        }

        String projectId = pathVariables.get("projectId");
        Permission permission = isReadMethod(request.getMethod()) ? Permission.READ : Permission.WRITE;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthorizationDecision decision = authorizationService.authorize(authentication, projectId, permission);

        if (decision.allowed()) {
            return true;
        }

        if (decision.failure() == Failure.UNAUTHENTICATED) {
            return writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication required");
        }
        return writeError(response, HttpServletResponse.SC_FORBIDDEN,
            "FORBIDDEN", "Project access denied");
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method)
            || "HEAD".equalsIgnoreCase(method)
            || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean writeError(HttpServletResponse response, int status, String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        return false;
    }
}
