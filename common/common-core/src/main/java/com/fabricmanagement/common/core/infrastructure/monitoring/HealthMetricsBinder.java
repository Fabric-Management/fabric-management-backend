package com.fabricmanagement.common.core.infrastructure.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Health check metrics binder for monitoring service health.
 * Provides health-related metrics for all services.
 */
@Component
public class HealthMetricsBinder implements MeterBinder {
    
    private MeterRegistry meterRegistry;
    
    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Records a health check result.
     *
     * @param serviceName the service name
     * @param component the component being checked
     * @param isHealthy whether the component is healthy
     */
    public void recordHealthCheck(String serviceName, String component, boolean isHealthy) {
        meterRegistry.gauge("health.check.status",
            "service", serviceName,
            "component", component,
            isHealthy ? 1.0 : 0.0);
    }
    
    /**
     * Records a dependency health status.
     *
     * @param serviceName the service name
     * @param dependencyName the dependency name
     * @param isHealthy whether the dependency is healthy
     */
    public void recordDependencyHealth(String serviceName, String dependencyName, boolean isHealthy) {
        meterRegistry.gauge("dependency.health.status",
            "service", serviceName,
            "dependency", dependencyName,
            isHealthy ? 1.0 : 0.0);
    }
}

