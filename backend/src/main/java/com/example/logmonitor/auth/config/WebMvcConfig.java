package com.example.logmonitor.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProjectSecurityInterceptor projectSecurityInterceptor;

    public WebMvcConfig(ProjectSecurityInterceptor projectSecurityInterceptor) {
        this.projectSecurityInterceptor = projectSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(projectSecurityInterceptor)
            .addPathPatterns("/api/v1/projects/{projectId}/**");
    }
}
