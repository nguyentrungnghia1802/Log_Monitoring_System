package com.example.logmonitor.ingestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestionLimitsProperties.class)
public class IngestionConfig {}
