package com.example.logmonitor.config;

import com.example.logmonitor.observability.MongoCommandMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoCommandMetricsCustomizer(MeterRegistry meterRegistry) {
        return builder -> builder.addCommandListener(new MongoCommandMetricsListener(meterRegistry));
    }
}
