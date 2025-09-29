package com.fabricmanagement.common.core.domain.specification;

/**
 * Negation specification that inverts the result of another specification.
 * This follows the Specification Pattern for complex business rules.
 * 
 * @param <T> the type of entity being validated
 */
public class NotSpecification<T> implements Specification<T> {
    
    private final Specification<T> specification;
    
    public NotSpecification(Specification<T> specification) {
        this.specification = specification;
    }
    
    @Override
    public boolean isSatisfiedBy(T entity) {
        return !specification.isSatisfiedBy(entity);
    }
    
    @Override
    public String getErrorMessage() {
        return "NOT (" + specification.getErrorMessage() + ")";
    }
    
    @Override
    public String getSpecificationName() {
        return "NotSpecification";
    }
}

