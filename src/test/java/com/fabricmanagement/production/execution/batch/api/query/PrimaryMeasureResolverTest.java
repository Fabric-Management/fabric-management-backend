package com.fabricmanagement.production.execution.batch.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import org.junit.jupiter.api.Test;

class PrimaryMeasureResolverTest {

  private final PrimaryMeasureResolver resolver = new PrimaryMeasureResolver();

  @Test
  void shouldResolvePrimaryMeasureFromProcessAndOutputProductType() {
    assertEquals(PrimaryMeasure.LENGTH, resolver.resolve("WEAVING", ProductType.FABRIC));
    assertEquals(PrimaryMeasure.WEIGHT, resolver.resolve("SPINNING", ProductType.YARN));
    assertEquals(PrimaryMeasure.LENGTH, resolver.resolve("PURCHASE", ProductType.FABRIC));
    assertEquals(PrimaryMeasure.WEIGHT, resolver.resolve("PURCHASE", ProductType.FIBER));
  }

  @Test
  void shouldDefaultUnknownClassificationToWeight() {
    assertEquals(PrimaryMeasure.WEIGHT, resolver.resolve("UNMAPPED_PROCESS", ProductType.CHEMICAL));
  }
}
