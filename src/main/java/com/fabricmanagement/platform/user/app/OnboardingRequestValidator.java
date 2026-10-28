package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.util.PhoneValidationUtil;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The command boundary also validates non-HTTP callers before any profile writes. */
@Component
@RequiredArgsConstructor
public class OnboardingRequestValidator {
  private final Validator validator;

  public void validate(CompleteOnboardingRequest request) {
    if (request == null) return;
    var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new PlatformDomainException(
          "Invalid onboarding profile", "ONBOARDING_PROFILE_INVALID", 422);
    }
    Map<String, String> nonBlankWhenPresent = new LinkedHashMap<>();
    nonBlankWhenPresent.put("organizationName", request.getOrganizationName());
    nonBlankWhenPresent.put("taxId", request.getTaxId());
    nonBlankWhenPresent.put("addressLine1", request.getAddressLine1());
    nonBlankWhenPresent.put("city", request.getCity());
    nonBlankWhenPresent.put("country", request.getCountry());
    nonBlankWhenPresent.put("addressLabel", request.getAddressLabel());
    nonBlankWhenPresent.put("companyEmail", request.getCompanyEmail());
    nonBlankWhenPresent.put("companyPhone", request.getCompanyPhone());
    nonBlankWhenPresent.put("mobilePhone", request.getMobilePhone());
    nonBlankWhenPresent.forEach(
        (field, value) -> {
          if (value != null && value.isBlank()) {
            throw new PlatformDomainException(
                    "Onboarding field must not be blank", "ONBOARDING_PROFILE_INVALID", 422)
                .withFieldError(field, "Must not be blank");
          }
        });
    if (request.getMobilePhone() != null
        && !PhoneValidationUtil.validateMobile(request.getMobilePhone()).isValid()) {
      throw new PlatformDomainException(
              "Invalid personal mobile", "ONBOARDING_PROFILE_INVALID", 422)
          .withFieldError("mobilePhone", "Invalid mobile number");
    }
  }
}
