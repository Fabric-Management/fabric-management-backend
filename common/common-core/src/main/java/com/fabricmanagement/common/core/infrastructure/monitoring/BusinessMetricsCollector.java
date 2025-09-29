package com.fabricmanagement.common.core.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics collector for tracking business-specific metrics.
 * Provides common business metrics collection capabilities.
 */
@Component
public class BusinessMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public BusinessMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Records a business event occurrence.
     *
     * @param eventType the type of business event
     * @param serviceName the service name
     * @param additionalTags additional tags
     */
    public void recordBusinessEvent(String eventType, String serviceName, String... additionalTags) {
        Counter.builder("business.event.count")
            .tag("event.type", eventType)
            .tag("service", serviceName)
            .tags(additionalTags)
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * Records a successful operation.
     *
     * @param operationType the type of operation
     * @param serviceName the service name
     */
    public void recordSuccess(String operationType, String serviceName) {
        Counter.builder("operation.success.count")
            .tag("operation.type", operationType)
            .tag("service", serviceName)
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * Records a failed operation.
     *
     * @param operationType the type of operation
     * @param serviceName the service name
     * @param errorType the type of error
     */
    public void recordFailure(String operationType, String serviceName, String errorType) {
        Counter.builder("operation.failure.count")
            .tag("operation.type", operationType)
            .tag("service", serviceName)
            .tag("error.type", errorType)
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * Records a validation failure.
     *
     * @param validationType the type of validation
     * @param serviceName the service name
     */
    public void recordValidationFailure(String validationType, String serviceName) {
        Counter.builder("validation.failure.count")
            .tag("validation.type", validationType)
            .tag("service", serviceName)
            .register(meterRegistry)
            .increment();
    }
}

