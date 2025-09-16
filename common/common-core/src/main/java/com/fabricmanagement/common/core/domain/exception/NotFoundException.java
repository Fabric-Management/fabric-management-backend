package com.fabricmanagement.common.core.domain.exception;

/**
 * Exception thrown when a requested resource is not found
 */
public class NotFoundException extends CoreDomainException {

    public NotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, "RESOURCE_NOT_FOUND", cause);
    }

    public NotFoundException(String resourceType, Object id) {
        super(String.format("%s with id %s not found", resourceType, id), "RESOURCE_NOT_FOUND");
    }
}
