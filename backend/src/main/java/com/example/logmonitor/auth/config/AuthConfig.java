package com.example.logmonitor.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, LocalAdminBootstrapProperties.class})
public class AuthConfig {
}
