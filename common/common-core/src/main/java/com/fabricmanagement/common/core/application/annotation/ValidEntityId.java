package com.fabricmanagement.common.core.application.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for entity ID validation.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidEntityIdValidator.class)
@Documented
public @interface ValidEntityId {

    String message() default "Invalid entity ID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Allow null values.
     */
    boolean allowNull() default false;
}
