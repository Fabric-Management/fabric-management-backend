package com.fabricmanagement.common.core.domain.specification;

/**
 * Base interface for all specifications following the Specification Pattern.
 * This provides a consistent contract for business rule validation.
 * 
 * @param <T> the type of entity being validated
 */
public interface Specification<T> {
    
    /**
     * Checks if the specification is satisfied by the given entity.
     *
     * @param entity the entity to validate
     * @return true if the specification is satisfied, false otherwise
     */
    boolean isSatisfiedBy(T entity);
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message describing why the specification failed
     */
    String getErrorMessage();
    
    /**
     * Gets the specification name for logging and debugging.
     *
     * @return the specification name
     */
    default String getSpecificationName() {
        return this.getClass().getSimpleName();
    }
}

