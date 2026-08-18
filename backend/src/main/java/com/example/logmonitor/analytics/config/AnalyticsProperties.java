package com.example.logmonitor.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics")
public class AnalyticsProperties {

    private int defaultRangeHours = 24;
    private int maxRangeHours = 168;
    private int maxBuckets = 1000;
    private int topServicesLimit = 5;
    private int topErrorsLimit = 10;

    public int getDefaultRangeHours() {
        return defaultRangeHours;
    }

    public void setDefaultRangeHours(int defaultRangeHours) {
        this.defaultRangeHours = defaultRangeHours;
    }

    public int getMaxRangeHours() {
        return maxRangeHours;
    }

    public void setMaxRangeHours(int maxRangeHours) {
        this.maxRangeHours = maxRangeHours;
    }

    public int getMaxBuckets() {
        return maxBuckets;
    }

    public void setMaxBuckets(int maxBuckets) {
        this.maxBuckets = maxBuckets;
    }

    public int getTopServicesLimit() {
        return topServicesLimit;
    }

    public void setTopServicesLimit(int topServicesLimit) {
        this.topServicesLimit = topServicesLimit;
    }

    public int getTopErrorsLimit() {
        return topErrorsLimit;
    }

    public void setTopErrorsLimit(int topErrorsLimit) {
        this.topErrorsLimit = topErrorsLimit;
    }
}
