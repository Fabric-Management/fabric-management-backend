package com.fabricmanagement.common.core.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for validation operations.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Validates an object and returns list of error messages.
     *
     * @param validator the validator instance
     * @param object the object to validate
     * @param <T> the type of object
     * @return list of validation error messages
     */
    public static <T> List<String> validate(Validator validator, T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return violations.stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.toList());
    }

    /**
     * Validates an object and throws exception if validation fails.
     *
     * @param validator the validator instance
     * @param object the object to validate
     * @param <T> the type of object
     * @throws IllegalArgumentException if validation fails
     */
    public static <T> void validateAndThrow(Validator validator, T object) {
        List<String> errors = validate(validator, object);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }

    /**
     * Checks if an object is valid.
     *
     * @param validator the validator instance
     * @param object the object to validate
     * @param <T> the type of object
     * @return true if object is valid
     */
    public static <T> boolean isValid(Validator validator, T object) {
        return validator.validate(object).isEmpty();
    }

    /**
     * Validates a specific property of an object.
     *
     * @param validator the validator instance
     * @param object the object to validate
     * @param propertyName the property name
     * @param <T> the type of object
     * @return list of validation error messages for the property
     */
    public static <T> List<String> validateProperty(Validator validator, T object, String propertyName) {
        Set<ConstraintViolation<T>> violations = validator.validateProperty(object, propertyName);
        return violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toList());
    }

    /**
     * Validates a property value.
     *
     * @param validator the validator instance
     * @param beanType the bean class
     * @param propertyName the property name
     * @param value the property value
     * @param <T> the type of bean
     * @return list of validation error messages
     */
    public static <T> List<String> validateValue(Validator validator, Class<T> beanType,
                                                String propertyName, Object value) {
        Set<ConstraintViolation<T>> violations = validator.validateValue(beanType, propertyName, value);
        return violations.stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toList());
    }
}
