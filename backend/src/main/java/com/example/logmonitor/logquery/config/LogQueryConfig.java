package com.example.logmonitor.logquery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LogQueryProperties.class)
public class LogQueryConfig {
}
