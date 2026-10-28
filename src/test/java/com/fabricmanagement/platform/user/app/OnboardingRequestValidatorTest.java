package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OnboardingRequestValidatorTest {
  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private final OnboardingRequestValidator validator =
      new OnboardingRequestValidator(FACTORY.getValidator());
  private final ObjectMapper json = new ObjectMapper();

  @AfterAll
  static void close() {
    FACTORY.close();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "organizationName",
        "taxId",
        "addressLine1",
        "city",
        "country",
        "addressLabel",
        "companyEmail",
        "companyPhone",
        "mobilePhone"
      })
  void blankRequiredOrIdentityDataCannotBeUsedToClear(String field) throws Exception {
    var request = json.readValue("{\"" + field + "\":\"   \"}", CompleteOnboardingRequest.class);
    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("ONBOARDING_PROFILE_INVALID");
              assertThat(failure.getHttpStatus()).isEqualTo(422);
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "legalName",
        "registrationNumber",
        "industry",
        "website",
        "description",
        "addressLine2",
        "state",
        "district",
        "postalCode"
      })
  void optionalScalarExplicitEmptyStringIsAccepted(String field) throws Exception {
    var request = json.readValue("{\"" + field + "\":\"\"}", CompleteOnboardingRequest.class);
    assertThat(request.hasCompanyChanges()).isTrue();
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  @Test
  void noBodyOmissionAndExplicitNullRemainValidNoChangeCommands() throws Exception {
    validator.validate(null);
    validator.validate(json.readValue("{}", CompleteOnboardingRequest.class));
    var explicitNull =
        json.readValue(
            "{\"legalName\":null,\"city\":null,\"mobilePhone\":null}",
            CompleteOnboardingRequest.class);
    validator.validate(explicitNull);
    assertThat(explicitNull.hasCompanyChanges()).isFalse();
  }

  static Stream<Arguments> invalidValues() {
    return Stream.of(
        Arguments.of("legalName", "x".repeat(201)),
        Arguments.of("organizationName", "x".repeat(256)),
        Arguments.of("taxId", "GB1"),
        Arguments.of("companyEmail", "invalid-email"),
        Arguments.of("mobilePhone", "07700900123"));
  }

  @ParameterizedTest
  @MethodSource("invalidValues")
  void internalCallersReceiveTheSameBeanValidationAsHttp(String field, String value)
      throws Exception {
    var request =
        json.readValue(
            json.writeValueAsString(java.util.Map.of(field, value)),
            CompleteOnboardingRequest.class);
    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(PlatformDomainException.class);
  }
}
