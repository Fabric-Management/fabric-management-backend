package com.fabricmanagement.common.core.domain.exception;

/**
 * Exception thrown when a requested entity is not found.
 */
public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("%s with id '%s' not found", entityName, id));
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
