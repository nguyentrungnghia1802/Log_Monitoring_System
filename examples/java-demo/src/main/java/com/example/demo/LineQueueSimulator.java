package com.example.demo;

import com.example.logmonitor.sdk.LogMonitoringClient;
import com.example.logmonitor.sdk.LogMonitoringClientConfig;

import java.util.Map;

public class LineQueueSimulator {

    public static void main(String[] args) {
        System.out.println("Starting LINE Smart Queue Event Simulator...");

        LogMonitoringClientConfig config = new LogMonitoringClientConfig()
            .setEndpoint("http://localhost:8080")
            .setApiKey("demo-api-key")
            .setService("line-smart-queue")
            .setEnvironment("production")
            .setQueueCapacity(1000)
            .setBatchSize(50);

        try (LogMonitoringClient client = new LogMonitoringClient(config)) {
            // Simulate normal queue activity
            for (int i = 1; i <= 5; i++) {
                boolean logged = client.log("INFO", "QUEUE_ENQUEUE", "Ticket #" + i + " enqueued for LINE Queue System", null, null, Map.of("ticketId", i));
                System.out.println("Enqueued ticket #" + i + " log result: " + logged);
            }

            // Simulate queue processing exception
            try {
                throw new IllegalStateException("LINE Notification Service connection timeout after 3000ms");
            } catch (Exception ex) {
                boolean errorLogged = client.error("QUEUE_DISPATCH_FAILED", "Failed to send LINE notification for ticket #3", ex);
                System.out.println("Error dispatch log result: " + errorLogged);
            }

            System.out.println("Simulation completed. Flushing remaining logs via client close...");
        }
    }
}
