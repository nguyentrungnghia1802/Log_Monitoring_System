package com.example.logmonitor.ingestion.infrastructure;

import com.example.logmonitor.ingestion.domain.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class IngestionQueue {

    private final BlockingQueue<LogEvent> queue;
    private final AtomicInteger acceptedCounter = new AtomicInteger();
    private final AtomicInteger rejectedCounter = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Counter acceptedMeter;
    private final Counter rejectedMeter;
    private final Counter shutdownRejectedMeter;

    public IngestionQueue(
        @Value("${ingestion.queue.capacity:50000}") int capacity,
        MeterRegistry registry
    ) {
        this.queue = new ArrayBlockingQueue<>(capacity);

        Gauge.builder("ingestion.queue.depth", queue, Collection::size)
            .description("Current depth of the log ingestion queue")
            .register(registry);

        Gauge.builder("ingestion.queue.capacity", queue, currentQueue ->
                currentQueue.size() + currentQueue.remainingCapacity())
            .description("Configured capacity of the log ingestion queue")
            .register(registry);

        this.acceptedMeter = Counter.builder("ingestion.accepted")
            .description("Total log events accepted into the queue")
            .register(registry);

        this.rejectedMeter = Counter.builder("ingestion.rejected.backpressure")
            .description("Total log events rejected because the ingestion queue is full")
            .register(registry);

        this.shutdownRejectedMeter = Counter.builder("ingestion.rejected.shutdown")
            .description("Total log events rejected because application shutdown has started")
            .register(registry);
    }

    public boolean hasCapacity(int count) {
        return queue.remainingCapacity() >= count;
    }

    public synchronized boolean offer(LogEvent event) {
        if (!accepting.get()) {
            rejectedCounter.incrementAndGet();
            shutdownRejectedMeter.increment();
            return false;
        }
        boolean accepted = queue.offer(event);
        if (accepted) {
            acceptedCounter.incrementAndGet();
            acceptedMeter.increment();
        } else {
            rejectedCounter.incrementAndGet();
            rejectedMeter.increment();
        }
        return accepted;
    }

    public boolean offerAll(List<LogEvent> events) {
        synchronized (this) {
            if (!accepting.get()) {
                rejectedCounter.addAndGet(events.size());
                shutdownRejectedMeter.increment(events.size());
                return false;
            }
            if (!hasCapacity(events.size())) {
                rejectedCounter.addAndGet(events.size());
                rejectedMeter.increment(events.size());
                return false;
            }
            for (LogEvent event : events) {
                queue.offer(event);
            }
            acceptedCounter.addAndGet(events.size());
            acceptedMeter.increment(events.size());
            return true;
        }
    }

    public LogEvent poll() {
        return queue.poll();
    }

    public LogEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return queue.remainingCapacity() + queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public synchronized void stopAccepting() {
        accepting.set(false);
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public int acceptedCount() {
        return acceptedCounter.get();
    }

    public int rejectedCount() {
        return rejectedCounter.get();
    }
}
