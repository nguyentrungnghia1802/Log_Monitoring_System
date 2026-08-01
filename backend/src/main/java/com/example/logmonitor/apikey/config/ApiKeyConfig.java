package com.example.logmonitor.apikey.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyConfig {}
