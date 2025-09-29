package com.fabricmanagement.common.core.infrastructure.adapter;

import com.fabricmanagement.common.core.infrastructure.monitoring.BusinessMetricsCollector;
import com.fabricmanagement.common.core.infrastructure.monitoring.PerformanceMonitor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base adapter class providing common functionality for all adapters.
 * This follows the Adapter Pattern for infrastructure layer components.
 */
public abstract class BaseAdapter {
    
    @Autowired
    protected PerformanceMonitor performanceMonitor;
    
    @Autowired
    protected BusinessMetricsCollector businessMetricsCollector;
    
    /**
     * Gets the adapter name for logging and metrics.
     *
     * @return the adapter name
     */
    protected abstract String getAdapterName();
    
    /**
     * Records a successful operation.
     *
     * @param operationType the type of operation
     */
    protected void recordSuccess(String operationType) {
        businessMetricsCollector.recordSuccess(operationType, getAdapterName());
    }
    
    /**
     * Records a failed operation.
     *
     * @param operationType the type of operation
     * @param errorType the type of error
     */
    protected void recordFailure(String operationType, String errorType) {
        businessMetricsCollector.recordFailure(operationType, getAdapterName(), errorType);
    }
    
    /**
     * Records a business event.
     *
     * @param eventType the type of event
     */
    protected void recordBusinessEvent(String eventType) {
        businessMetricsCollector.recordBusinessEvent(eventType, getAdapterName());
    }
}

