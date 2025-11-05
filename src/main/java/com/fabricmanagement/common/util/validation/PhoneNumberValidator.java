package com.fabricmanagement.common.util.validation;

import com.fabricmanagement.common.util.PhoneValidationUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Phone number validator implementation.
 *
 * <p>Validates phone numbers using PhoneValidationUtil.</p>
 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private String countryCode;

    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
        this.countryCode = constraintAnnotation.country();
    }

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.isBlank()) {
            // Let @NotNull or @NotBlank handle null/blank validation
            return true;
        }

        PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validate(phone, countryCode);

        if (!result.isValid()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(result.getErrorMessage())
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}

