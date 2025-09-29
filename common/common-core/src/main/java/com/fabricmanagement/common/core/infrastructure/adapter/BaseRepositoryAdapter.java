package com.fabricmanagement.common.core.infrastructure.adapter;

import com.fabricmanagement.common.core.infrastructure.monitoring.PerformanceMonitor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base repository adapter providing common functionality for all repository adapters.
 * This follows the Adapter Pattern for persistence layer components.
 */
public abstract class BaseRepositoryAdapter {
    
    @Autowired
    protected PerformanceMonitor performanceMonitor;
    
    /**
     * Gets the repository adapter name for logging and metrics.
     *
     * @return the repository adapter name
     */
    protected abstract String getRepositoryName();
    
    /**
     * Measures repository operation execution time.
     *
     * @param operation the operation to measure
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if the operation fails
     */
    protected <T> T measureRepositoryOperation(String operation, java.util.concurrent.Callable<T> callable) throws Exception {
        return performanceMonitor.measureExecution(getRepositoryName() + "." + operation, callable);
    }
    
    /**
     * Measures repository operation execution time for void operations.
     *
     * @param operation the operation to measure
     * @param runnable the operation to measure
     */
    protected void measureRepositoryOperation(String operation, Runnable runnable) {
        performanceMonitor.measureExecution(getRepositoryName() + "." + operation, runnable);
    }
}

