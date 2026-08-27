package com.fabricmanagement.product.yarn.domain.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.core.domain.registry.UnitFamily;
import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YarnVocabularyTest {

  @Test
  void constructionFeatureMembershipIsTheClosedV1Contract() {
    assertThat(EnumSet.copyOf(Arrays.asList(YarnConstructionFeature.values())))
        .containsExactlyInAnyOrder(
            YarnConstructionFeature.CORE_SPUN,
            YarnConstructionFeature.SIRO,
            YarnConstructionFeature.SLUB,
            YarnConstructionFeature.COVERED);
  }

  @Test
  void everyCountSystemDerivesItsLinearDensityFamilyFromUnitCode() {
    assertThat(Arrays.stream(CountSystem.values()).map(CountSystem::unitCode))
        .allMatch(unitCode -> unitCode.unitFamily() == UnitFamily.LINEAR_DENSITY)
        .doesNotHaveDuplicates();
  }

  @Test
  void yarnCatalogueEntitiesDoNotDeclareMeasuredPropertyColumns() {
    Set<String> forbiddenColumns = Set.of("unit", "canonical_unit_code", "conversion_policy");
    assertThat(
            Arrays.stream(
                    new Class<?>[] {
                      YarnSpinningSystem.class, YarnEndUse.class, YarnTestMethod.class
                    })
                .filter(type -> type.isAnnotationPresent(Entity.class))
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(YarnVocabularyTest::columnName)
                .filter(forbiddenColumns::contains))
        .isEmpty();
  }

  private static String columnName(Field field) {
    Column column = field.getAnnotation(Column.class);
    return column == null || column.name().isBlank() ? field.getName() : column.name();
  }
}
