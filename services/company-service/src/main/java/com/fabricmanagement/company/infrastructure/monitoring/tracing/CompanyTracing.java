package com.fabricmanagement.company.infrastructure.monitoring.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * Tracing utilities for company service operations.
 * Provides distributed tracing capabilities for observability.
 */
@Component
public class CompanyTracing {
    
    private final Tracer tracer;
    
    public CompanyTracing(Tracer tracer) {
        this.tracer = tracer;
    }
    
    /**
     * Creates a new span for company operations.
     *
     * @param operationName the name of the operation
     * @return the created span
     */
    public Span createSpan(String operationName) {
        return tracer.nextSpan()
                .name(operationName)
                .tag("service", "company-service")
                .start();
    }
    
    /**
     * Creates a span for company creation operations.
     *
     * @param companyId the company ID
     * @return the created span
     */
    public Span createCompanyCreationSpan(String companyId) {
        return tracer.nextSpan()
                .name("company.create")
                .tag("service", "company-service")
                .tag("company.id", companyId)
                .start();
    }
    
    /**
     * Creates a span for company update operations.
     *
     * @param companyId the company ID
     * @return the created span
     */
    public Span createCompanyUpdateSpan(String companyId) {
        return tracer.nextSpan()
                .name("company.update")
                .tag("service", "company-service")
                .tag("company.id", companyId)
                .start();
    }
    
    /**
     * Creates a span for company query operations.
     *
     * @param queryType the type of query
     * @return the created span
     */
    public Span createCompanyQuerySpan(String queryType) {
        return tracer.nextSpan()
                .name("company.query")
                .tag("service", "company-service")
                .tag("query.type", queryType)
                .start();
    }
    
    /**
     * Adds an error tag to a span.
     *
     * @param span the span
     * @param error the error message
     */
    public void addErrorTag(Span span, String error) {
        span.tag("error", "true");
        span.tag("error.message", error);
    }
    
    /**
     * Adds a success tag to a span.
     *
     * @param span the span
     */
    public void addSuccessTag(Span span) {
        span.tag("success", "true");
    }
}
