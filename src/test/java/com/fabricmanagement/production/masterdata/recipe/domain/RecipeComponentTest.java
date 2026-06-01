package com.fabricmanagement.production.masterdata.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecipeComponentTest {

  @Test
  void normalizeFields_shouldUseRootLocaleAndConvertBlankValuesToNull() {
    Locale previousLocale = Locale.getDefault();

    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      RecipeComponent component =
          RecipeComponent.builder().certification(" bci ").origin(" it ").build();

      ReflectionTestUtils.invokeMethod(component, "normalizeFields");

      assertThat(component.getCertification()).isEqualTo("BCI");
      assertThat(component.getOrigin()).isEqualTo("IT");

      RecipeComponent blankComponent =
          RecipeComponent.builder().certification("   ").origin("\t").build();

      ReflectionTestUtils.invokeMethod(blankComponent, "normalizeFields");

      assertThat(blankComponent.getCertification()).isNull();
      assertThat(blankComponent.getOrigin()).isNull();
    } finally {
      Locale.setDefault(previousLocale);
    }
  }
}
