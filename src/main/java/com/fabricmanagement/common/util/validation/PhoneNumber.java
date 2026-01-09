package com.fabricmanagement.common.util.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Phone number validation annotation.
 *
 * <p>Validates phone numbers in E.164 format with optional country-specific validation.
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * @PhoneNumber(country = "TR")
 * private String phoneNumber;
 * }</pre>
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

  String message() default "Invalid phone number format";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /**
   * Country code for country-specific validation (ISO 3166-1 alpha-2). If not specified, only E.164
   * format is validated.
   *
   * @return Country code (e.g., "TR", "GB", "US")
   */
  String country() default "";
}
