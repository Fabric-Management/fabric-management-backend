package com.fabricmanagement.user.infrastructure.monitoring.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * Tracing utilities for user service operations.
 * Provides distributed tracing capabilities for observability.
 */
@Component
public class UserTracing {
    
    private final Tracer tracer;
    
    public UserTracing(Tracer tracer) {
        this.tracer = tracer;
    }
    
    /**
     * Creates a new span for user operations.
     *
     * @param operationName the name of the operation
     * @return the created span
     */
    public Span createSpan(String operationName) {
        return tracer.nextSpan()
                .name(operationName)
                .tag("service", "user-service")
                .start();
    }
    
    /**
     * Creates a span for user creation operations.
     *
     * @param userId the user ID
     * @return the created span
     */
    public Span createUserCreationSpan(String userId) {
        return tracer.nextSpan()
                .name("user.create")
                .tag("service", "user-service")
                .tag("user.id", userId)
                .start();
    }
    
    /**
     * Creates a span for user update operations.
     *
     * @param userId the user ID
     * @return the created span
     */
    public Span createUserUpdateSpan(String userId) {
        return tracer.nextSpan()
                .name("user.update")
                .tag("service", "user-service")
                .tag("user.id", userId)
                .start();
    }
    
    /**
     * Creates a span for user query operations.
     *
     * @param queryType the type of query
     * @return the created span
     */
    public Span createUserQuerySpan(String queryType) {
        return tracer.nextSpan()
                .name("user.query")
                .tag("service", "user-service")
                .tag("query.type", queryType)
                .start();
    }
    
    /**
     * Creates a span for user authentication operations.
     *
     * @param username the username
     * @return the created span
     */
    public Span createUserAuthSpan(String username) {
        return tracer.nextSpan()
                .name("user.auth")
                .tag("service", "user-service")
                .tag("username", username)
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
