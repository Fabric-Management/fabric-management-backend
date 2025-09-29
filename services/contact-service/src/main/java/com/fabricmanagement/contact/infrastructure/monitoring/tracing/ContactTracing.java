package com.fabricmanagement.contact.infrastructure.monitoring.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * Tracing utilities for contact service operations.
 * Provides distributed tracing capabilities for observability.
 */
@Component
public class ContactTracing {
    
    private final Tracer tracer;
    
    public ContactTracing(Tracer tracer) {
        this.tracer = tracer;
    }
    
    /**
     * Creates a new span for contact operations.
     *
     * @param operationName the name of the operation
     * @return the created span
     */
    public Span createSpan(String operationName) {
        return tracer.nextSpan()
                .name(operationName)
                .tag("service", "contact-service")
                .start();
    }
    
    /**
     * Creates a span for contact creation operations.
     *
     * @param contactId the contact ID
     * @return the created span
     */
    public Span createContactCreationSpan(String contactId) {
        return tracer.nextSpan()
                .name("contact.create")
                .tag("service", "contact-service")
                .tag("contact.id", contactId)
                .start();
    }
    
    /**
     * Creates a span for contact update operations.
     *
     * @param contactId the contact ID
     * @return the created span
     */
    public Span createContactUpdateSpan(String contactId) {
        return tracer.nextSpan()
                .name("contact.update")
                .tag("service", "contact-service")
                .tag("contact.id", contactId)
                .start();
    }
    
    /**
     * Creates a span for contact query operations.
     *
     * @param queryType the type of query
     * @return the created span
     */
    public Span createContactQuerySpan(String queryType) {
        return tracer.nextSpan()
                .name("contact.query")
                .tag("service", "contact-service")
                .tag("query.type", queryType)
                .start();
    }
    
    /**
     * Creates a span for email operations.
     *
     * @param contactId the contact ID
     * @param operation the email operation
     * @return the created span
     */
    public Span createEmailOperationSpan(String contactId, String operation) {
        return tracer.nextSpan()
                .name("contact.email." + operation)
                .tag("service", "contact-service")
                .tag("contact.id", contactId)
                .tag("operation", operation)
                .start();
    }
    
    /**
     * Creates a span for phone operations.
     *
     * @param contactId the contact ID
     * @param operation the phone operation
     * @return the created span
     */
    public Span createPhoneOperationSpan(String contactId, String operation) {
        return tracer.nextSpan()
                .name("contact.phone." + operation)
                .tag("service", "contact-service")
                .tag("contact.id", contactId)
                .tag("operation", operation)
                .start();
    }
    
    /**
     * Creates a span for address operations.
     *
     * @param contactId the contact ID
     * @param operation the address operation
     * @return the created span
     */
    public Span createAddressOperationSpan(String contactId, String operation) {
        return tracer.nextSpan()
                .name("contact.address." + operation)
                .tag("service", "contact-service")
                .tag("contact.id", contactId)
                .tag("operation", operation)
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
