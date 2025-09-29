package com.fabricmanagement.common.core.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Base metrics collector for all microservices.
 * Provides common metrics collection capabilities.
 */
@Component
public class BaseMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public BaseMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Creates a counter metric.
     *
     * @param name the metric name
     * @param description the metric description
     * @param tags the metric tags
     * @return the counter
     */
    public Counter createCounter(String name, String description, String... tags) {
        return Counter.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }
    
    /**
     * Creates a timer metric.
     *
     * @param name the metric name
     * @param description the metric description
     * @param tags the metric tags
     * @return the timer
     */
    public Timer createTimer(String name, String description, String... tags) {
        return Timer.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }
    
    /**
     * Records a counter increment.
     *
     * @param name the metric name
     * @param amount the amount to increment
     * @param tags the metric tags
     */
    public void incrementCounter(String name, double amount, String... tags) {
        Counter.builder(name)
            .tags(tags)
            .register(meterRegistry)
            .increment(amount);
    }
    
    /**
     * Records a timer sample.
     *
     * @param name the metric name
     * @param duration the duration to record
     * @param tags the metric tags
     */
    public void recordTimer(String name, java.time.Duration duration, String... tags) {
        Timer.builder(name)
            .tags(tags)
            .register(meterRegistry)
            .record(duration);
    }
}

