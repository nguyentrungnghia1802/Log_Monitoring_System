package com.example.logmonitor.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedactionProperties.class)
public class RedactionConfig {}
