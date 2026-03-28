package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocalizationContext Unit Tests")
class LocalizationContextTest {

  @AfterEach
  void cleanup() {
    LocalizationContext.clear();
  }

  @Nested
  @DisplayName("getLocale()")
  class GetLocaleTests {

    @Test
    @DisplayName("locale set edilmemişse 'en' döner")
    void should_return_en_when_not_set() {
      assertThat(LocalizationContext.getLocale()).isEqualTo("en");
    }

    @Test
    @DisplayName("set edilen locale geri döner")
    void should_return_set_locale() {
      LocalizationContext.setLocale("tr");
      assertThat(LocalizationContext.getLocale()).isEqualTo("tr");
    }
  }

  @Nested
  @DisplayName("getTimezone()")
  class GetTimezoneTests {

    @Test
    @DisplayName("timezone set edilmemişse null döner")
    void should_return_null_when_not_set() {
      assertThat(LocalizationContext.getTimezone()).isNull();
    }

    @Test
    @DisplayName("set edilen timezone geri döner")
    void should_return_set_timezone() {
      LocalizationContext.setTimezone("Europe/Istanbul");
      assertThat(LocalizationContext.getTimezone()).isEqualTo("Europe/Istanbul");
    }
  }

  @Nested
  @DisplayName("clear()")
  class ClearTests {

    @Test
    @DisplayName("clear sonrası locale 'en' default'una döner")
    void should_reset_to_defaults_after_clear() {
      LocalizationContext.setLocale("tr");
      LocalizationContext.setTimezone("Europe/Istanbul");

      LocalizationContext.clear();

      assertThat(LocalizationContext.getLocale()).isEqualTo("en");
      assertThat(LocalizationContext.getTimezone()).isNull();
    }
  }
}
