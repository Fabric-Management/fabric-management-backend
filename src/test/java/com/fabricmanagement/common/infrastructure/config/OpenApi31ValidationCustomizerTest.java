package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OpenApi31ValidationCustomizerTest {

  private final OpenApi31ValidationCustomizer customizer = new OpenApi31ValidationCustomizer();

  @Test
  void convertsExclusiveMinimumToOpenApi31NumericBound() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);
    schema.setExclusiveMinimum(true);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getExclusiveMinimumValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getMinimum()).isNull();
    assertThat(schema.getExclusiveMinimum()).isNull();
  }

  @Test
  void convertsExclusiveMaximumToOpenApi31NumericBound() {
    Schema<?> schema = new Schema<>();
    schema.setMaximum(BigDecimal.valueOf(100));
    schema.setExclusiveMaximum(true);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getExclusiveMaximumValue()).isEqualByComparingTo("100");
    assertThat(schema.getMaximum()).isNull();
    assertThat(schema.getExclusiveMaximum()).isNull();
  }

  @Test
  void leavesInclusiveMinimumUnchanged() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.TEN);
    schema.setExclusiveMinimum(false);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.TEN);
    assertThat(schema.getExclusiveMinimum()).isFalse();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  @Test
  void leavesPositiveOrZeroStyleMinimumInclusive() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getExclusiveMinimum()).isNull();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  @Test
  void traversesNestedPropertiesArrayItemsAndComposedSchemasWithoutCycles() {
    Schema<?> itemSchema = new Schema<>();
    itemSchema.setMinimum(BigDecimal.ZERO);
    itemSchema.setExclusiveMinimum(true);

    Schema<?> nestedSchema = new Schema<>();
    nestedSchema.addProperty("values", new ArraySchema().items(itemSchema));

    Schema<?> rootSchema = new Schema<>();
    rootSchema.addAllOfItem(nestedSchema);
    nestedSchema.addProperty("parent", rootSchema);

    customizer.customise(openApi(SpecVersion.V31, rootSchema));

    assertThat(itemSchema.getExclusiveMinimumValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(itemSchema.getMinimum()).isNull();
    assertThat(itemSchema.getExclusiveMinimum()).isNull();
  }

  @Test
  void doesNotModifyOpenApi30Document() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);
    schema.setExclusiveMinimum(true);

    customizer.customise(openApi(SpecVersion.V30, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getExclusiveMinimum()).isTrue();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  private OpenAPI openApi(SpecVersion specVersion, Schema<?> schema) {
    return new OpenAPI(specVersion).components(new Components().addSchemas("TestSchema", schema));
  }
}
