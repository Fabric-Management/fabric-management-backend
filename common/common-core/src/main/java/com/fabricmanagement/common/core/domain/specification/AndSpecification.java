package com.fabricmanagement.common.core.domain.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite specification that combines multiple specifications with AND logic.
 * This follows the Specification Pattern for complex business rules.
 * 
 * @param <T> the type of entity being validated
 */
public class AndSpecification<T> implements Specification<T> {
    
    private final List<Specification<T>> specifications;
    
    public AndSpecification(List<Specification<T>> specifications) {
        this.specifications = new ArrayList<>(specifications);
    }
    
    @Override
    public boolean isSatisfiedBy(T entity) {
        return specifications.stream()
            .allMatch(spec -> spec.isSatisfiedBy(entity));
    }
    
    @Override
    public String getErrorMessage() {
        List<String> errorMessages = new ArrayList<>();
        for (Specification<T> spec : specifications) {
            errorMessages.add(spec.getErrorMessage());
        }
        return String.join(" AND ", errorMessages);
    }
    
    @Override
    public String getSpecificationName() {
        return "AndSpecification";
    }
}

