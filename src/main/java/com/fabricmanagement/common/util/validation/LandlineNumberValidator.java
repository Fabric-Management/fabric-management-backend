package com.fabricmanagement.common.util.validation;

import com.fabricmanagement.common.util.PhoneValidationUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LandlineNumberValidator implements ConstraintValidator<LandlineNumber, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true;
    }

    PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validateLandline(value);
    if (!result.isValid()) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(result.getErrorMessage())
          .addConstraintViolation();
      return false;
    }
    return true;
  }
}
