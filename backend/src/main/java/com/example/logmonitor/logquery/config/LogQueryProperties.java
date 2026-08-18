package com.example.logmonitor.logquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class LogQueryProperties {

    private int defaultRangeHours = 1;
    private int maxRangeHours = 168;
    private int defaultPageSize = 50;
    private int maxPageSize = 200;
    private int maxSearchLength = 128;
    private int maxFilterLength = 256;

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

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxSearchLength() {
        return maxSearchLength;
    }

    public void setMaxSearchLength(int maxSearchLength) {
        this.maxSearchLength = maxSearchLength;
    }

    public int getMaxFilterLength() {
        return maxFilterLength;
    }

    public void setMaxFilterLength(int maxFilterLength) {
        this.maxFilterLength = maxFilterLength;
    }
}
