package com.fabricmanagement.common.core.application.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for ValidEntityId annotation.
 */
public class ValidEntityIdValidator implements ConstraintValidator<ValidEntityId, Long> {

    private boolean allowNull;

    @Override
    public void initialize(ValidEntityId constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) {
            return allowNull;
        }

        return value > 0;
    }
}
