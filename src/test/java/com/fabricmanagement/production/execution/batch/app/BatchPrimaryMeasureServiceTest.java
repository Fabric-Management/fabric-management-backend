package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BatchPrimaryMeasureServiceTest {

  private final BatchPrimaryMeasureService service = new BatchPrimaryMeasureService();

  @Test
  void resolvesStableMeasureAndCanonicalUnitFromProductTypeOnly() {
    assertThat(service.resolve(ProductType.FABRIC))
        .isEqualTo(new BatchPrimaryMeasureService.Resolution(PrimaryMeasure.LENGTH, "M"));
    assertThat(service.resolve(ProductType.YARN))
        .isEqualTo(new BatchPrimaryMeasureService.Resolution(PrimaryMeasure.WEIGHT, "KG"));
    assertThat(service.resolve(ProductType.FIBER))
        .isEqualTo(new BatchPrimaryMeasureService.Resolution(PrimaryMeasure.WEIGHT, "KG"));
  }

  @Test
  void rejectsProductTypesOutsideTheCanonicalStockModel() {
    assertThatThrownBy(() -> service.resolve(ProductType.CHEMICAL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CHEMICAL");
    assertThatThrownBy(() -> service.resolve(ProductType.CONSUMABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CONSUMABLE");
    assertThatThrownBy(() -> service.resolve((ProductType) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void performsOnlyClosedExactMetricConversions() {
    assertThat(service.toCanonical(decimal("2"), " mt ", PrimaryMeasure.WEIGHT))
        .contains(decimal("2000"));
    assertThat(service.toCanonical(decimal("2500"), "G", PrimaryMeasure.WEIGHT))
        .contains(decimal("2.5"));
    assertThat(service.toCanonical(decimal("250"), "CM", PrimaryMeasure.LENGTH))
        .contains(decimal("2.5"));
    assertThat(service.toCanonical(decimal("2500"), "MM", PrimaryMeasure.LENGTH))
        .contains(decimal("2.5"));
    assertThat(service.toCanonical(decimal("10"), "LB", PrimaryMeasure.WEIGHT)).isEmpty();
    assertThat(service.toCanonical(decimal("10"), "KG", PrimaryMeasure.LENGTH)).isEmpty();
  }

  @Test
  void convertsCanonicalQuantitiesBackToBatchBookkeepingUnits() {
    assertThat(service.fromCanonical(decimal("2000"), "MT", PrimaryMeasure.WEIGHT))
        .contains(decimal("2"));
    assertThat(service.fromCanonical(decimal("2.5"), "CM", PrimaryMeasure.LENGTH))
        .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(decimal("250")));
  }

  private BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }
}
