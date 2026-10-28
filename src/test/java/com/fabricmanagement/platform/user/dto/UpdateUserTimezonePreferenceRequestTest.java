package com.fabricmanagement.platform.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateUserTimezonePreferenceRequestTest {
  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"UTC", "Europe/London", "America/New_York"})
  void acceptsRecognisedTimezones(String timezone) {
    assertThat(VALIDATOR.validate(new UpdateUserTimezonePreferenceRequest(timezone))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "invalid/zone", "Europe/Unknown"})
  void rejectsMissingAndUnrecognisedTimezones(String timezone) {
    assertThat(VALIDATOR.validate(new UpdateUserTimezonePreferenceRequest(timezone))).isNotEmpty();
  }
}
