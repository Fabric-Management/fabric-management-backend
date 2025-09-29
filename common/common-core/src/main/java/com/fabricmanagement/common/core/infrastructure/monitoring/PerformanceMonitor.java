package com.fabricmanagement.common.core.infrastructure.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Performance monitoring utility for measuring execution time.
 * Provides consistent performance tracking across all services.
 */
@Component
public class PerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    
    public PerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Measures the execution time of a callable operation.
     *
     * @param operationName the name of the operation
     * @param callable the operation to measure
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    public <T> T measureExecution(String operationName, Callable<T> callable) throws Exception {
        Timer timer = Timer.builder("operation.execution.time")
            .tag("operation", operationName)
            .register(meterRegistry);
        
        return timer.recordCallable(callable);
    }
    
    /**
     * Measures the execution time of a runnable operation.
     *
     * @param operationName the name of the operation
     * @param runnable the operation to measure
     */
    public void measureExecution(String operationName, Runnable runnable) {
        Timer timer = Timer.builder("operation.execution.time")
            .tag("operation", operationName)
            .register(meterRegistry);
        
        timer.record(runnable);
    }
    
    /**
     * Records a custom duration measurement.
     *
     * @param operationName the name of the operation
     * @param duration the duration to record
     */
    public void recordDuration(String operationName, Duration duration) {
        Timer timer = Timer.builder("operation.execution.time")
            .tag("operation", operationName)
            .register(meterRegistry);
        
        timer.record(duration);
    }
}

