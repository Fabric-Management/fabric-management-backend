package com.fabricmanagement.common.core.infrastructure.adapter;

import com.fabricmanagement.common.core.infrastructure.monitoring.PerformanceMonitor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base external service adapter providing common functionality for all external service adapters.
 * This follows the Adapter Pattern for external service integrations.
 */
public abstract class BaseExternalServiceAdapter {
    
    @Autowired
    protected PerformanceMonitor performanceMonitor;
    
    /**
     * Gets the external service adapter name for logging and metrics.
     *
     * @return the external service adapter name
     */
    protected abstract String getServiceName();
    
    /**
     * Measures external service call execution time.
     *
     * @param operation the operation to measure
     * @param callable the operation to measure
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    protected <T> T measureExternalServiceCall(String operation, java.util.concurrent.Callable<T> callable) throws Exception {
        return performanceMonitor.measureExecution(getServiceName() + "." + operation, callable);
    }
    
    /**
     * Measures external service call execution time for void operations.
     *
     * @param operation the operation to measure
     * @param runnable the operation to measure
     */
    protected void measureExternalServiceCall(String operation, Runnable runnable) {
        performanceMonitor.measureExecution(getServiceName() + "." + operation, runnable);
    }
}

