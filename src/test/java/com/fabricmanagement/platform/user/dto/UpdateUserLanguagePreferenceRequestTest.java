package com.fabricmanagement.platform.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateUserLanguagePreferenceRequestTest {
  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "en-GB", "tr", "TR-tr"})
  void acceptsSupportedLanguageTags(String locale) {
    assertThat(VALIDATOR.validate(new UpdateUserLanguagePreferenceRequest(locale))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "de", "english", "en_$", "tr_TR"})
  void rejectsMissingOrUnsupportedLanguageTags(String locale) {
    assertThat(VALIDATOR.validate(new UpdateUserLanguagePreferenceRequest(locale))).isNotEmpty();
  }
}
