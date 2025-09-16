package com.fabricmanagement.common.core.infrastructure.persistence.specification;

import com.fabricmanagement.common.core.application.dto.FilterCriteria;
import com.fabricmanagement.common.core.domain.base.BaseEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * Generic specification builder for dynamic queries.
 */
public class GenericSpecification {

    private GenericSpecification() {
        // Utility class
    }

    /**
     * Creates a specification from filter criteria.
     *
     * @param criteria the filter criteria
     * @param <T> the entity type
     * @return the specification
     */
    public static <T extends BaseEntity> Specification<T> fromCriteria(FilterCriteria criteria) {
        return (root, query, criteriaBuilder) -> buildPredicate(criteria, root, criteriaBuilder);
    }

    /**
     * Creates a specification for non-deleted entities.
     *
     * @param <T> the entity type
     * @return the specification
     */
    public static <T extends BaseEntity> Specification<T> isNotDeleted() {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("deleted"), false);
    }

    @SuppressWarnings("unchecked")
    private static <T> Predicate buildPredicate(FilterCriteria criteria, Root<T> root, CriteriaBuilder cb) {
        Path<Object> field = getPath(root, criteria.getField());
        Object value = criteria.getValue();
        Object secondValue = criteria.getSecondValue();

        return switch (criteria.getOperation()) {
            case EQUALS -> cb.equal(field, value);
            case NOT_EQUALS -> cb.notEqual(field, value);
            case LIKE -> cb.like(field.as(String.class), "%" + value + "%");
            case CONTAINS -> cb.like(cb.lower(field.as(String.class)),
                                   "%" + value.toString().toLowerCase() + "%");
            case STARTS_WITH -> cb.like(field.as(String.class), value + "%");
            case ENDS_WITH -> cb.like(field.as(String.class), "%" + value);
            case GREATER_THAN -> {
                if (value instanceof Comparable) {
                    yield cb.greaterThan(field.as(Comparable.class), (Comparable) value);
                }
                throw new IllegalArgumentException("Value must be comparable for GREATER_THAN operation");
            }
            case GREATER_THAN_OR_EQUAL -> {
                if (value instanceof Comparable) {
                    yield cb.greaterThanOrEqualTo(field.as(Comparable.class), (Comparable) value);
                }
                throw new IllegalArgumentException("Value must be comparable for GREATER_THAN_OR_EQUAL operation");
            }
            case LESS_THAN -> {
                if (value instanceof Comparable) {
                    yield cb.lessThan(field.as(Comparable.class), (Comparable) value);
                }
                throw new IllegalArgumentException("Value must be comparable for LESS_THAN operation");
            }
            case LESS_THAN_OR_EQUAL -> {
                if (value instanceof Comparable) {
                    yield cb.lessThanOrEqualTo(field.as(Comparable.class), (Comparable) value);
                }
                throw new IllegalArgumentException("Value must be comparable for LESS_THAN_OR_EQUAL operation");
            }
            case BETWEEN -> {
                if (value instanceof Comparable && secondValue instanceof Comparable) {
                    yield cb.between(field.as(Comparable.class), (Comparable) value, (Comparable) secondValue);
                }
                throw new IllegalArgumentException("Both values must be comparable for BETWEEN operation");
            }
            case IN -> {
                if (value instanceof Collection) {
                    yield field.in((Collection<?>) value);
                }
                throw new IllegalArgumentException("Value must be a collection for IN operation");
            }
            case NOT_IN -> {
                if (value instanceof Collection) {
                    yield cb.not(field.in((Collection<?>) value));
                }
                throw new IllegalArgumentException("Value must be a collection for NOT_IN operation");
            }
            case IS_NULL -> cb.isNull(field);
            case IS_NOT_NULL -> cb.isNotNull(field);
        };
    }

    private static Path<Object> getPath(Root<?> root, String fieldName) {
        String[] fields = fieldName.split("\\.");
        Path<Object> path = root.get(fields[0]);

        for (int i = 1; i < fields.length; i++) {
            path = path.get(fields[i]);
        }

        return path;
    }
}
