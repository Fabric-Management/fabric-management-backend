package com.fabricmanagement.product.yarn.domain.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.app.YarnArticleSpecCommand;
import jakarta.persistence.Column;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YarnArticlePersistenceContractTest {

  @Test
  void articleTablesIntroduceNoSecondMeasuredPropertyVocabulary() {
    Set<String> forbidden = Set.of("unit", "canonical_unit_code", "conversion_policy");
    assertThat(
            Arrays.stream(
                    new Class<?>[] {
                      YarnArticle.class,
                      YarnArticleComposition.class,
                      YarnArticleStructureComponent.class,
                      YarnArticleTwistStage.class,
                      YarnArticleConstructionFeature.class,
                      YarnArticleAudit.class
                    })
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(YarnArticlePersistenceContractTest::columnName)
                .filter(forbidden::contains))
        .isEmpty();
  }

  @Test
  void derivedValuesHaveNoSetterConstructorOrApplicationCommandInput() {
    Set<String> derivedNames =
        Set.of(
            "resultantLinearDensityTex",
            "componentLinearDensityTex",
            "canonicalDesignation",
            "canonicalKey");
    assertThat(Arrays.stream(YarnArticle.class.getMethods()).map(Method::getName))
        .noneMatch(
            name -> name.startsWith("set") && derivedNames.stream().anyMatch(name::contains));
    assertThat(
            Arrays.stream(YarnArticle.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes))
        .allMatch(parameters -> parameters.length == 0);
    assertThat(
            Arrays.stream(YarnArticleSpecCommand.class.getRecordComponents())
                .map(component -> component.getName()))
        .doesNotContainAnyElementsOf(derivedNames);
    assertThat(
            Arrays.stream(YarnArticleSpecCommand.ComponentCommand.class.getRecordComponents())
                .map(component -> component.getName()))
        .doesNotContain("componentLinearDensityTex");
  }

  private static String columnName(Field field) {
    Column column = field.getAnnotation(Column.class);
    return column == null || column.name().isBlank() ? field.getName() : column.name();
  }
}
