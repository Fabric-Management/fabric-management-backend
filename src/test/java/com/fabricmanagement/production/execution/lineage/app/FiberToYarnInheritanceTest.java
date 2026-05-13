package com.fabricmanagement.production.execution.lineage.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.lineage.domain.rule.AttributeInheritanceSchema;
import com.fabricmanagement.production.execution.lineage.domain.rule.BatchAttributes;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceAction;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceRule;
import com.fabricmanagement.production.execution.lineage.infra.configuration.AttributeInheritanceSchemaLoader;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BatchAttributeInheritanceEngine} using a programmatic FIBER→YARN schema
 * (mirroring fiber-to-yarn.json). Verifies WEIGHTED_AVERAGE, MIN, REQUIRE_EQUAL, COLLECT_TO_ARRAY,
 * DROP, and conflict behaviour for REQUIRE_EQUAL.
 */
@ExtendWith(MockitoExtension.class)
class FiberToYarnInheritanceTest {

  @Mock private AttributeInheritanceSchemaLoader schemaLoader;

  private BatchAttributeInheritanceEngine engine;

  private static final BigDecimal QTY_A = new BigDecimal("600");
  private static final BigDecimal QTY_B = new BigDecimal("400");

  private BatchAttributes batchA;
  private BatchAttributes batchB;

  /**
   * Builds a schema equivalent to fiber-to-yarn.json: fiber_micronaire (WEIGHTED_AVERAGE),
   * fiber_staple_length (MIN), fiber_grade → raw_fiber_grade (REQUIRE_EQUAL), fiber_shade →
   * raw_fiber_shade (REQUIRE_EQUAL), fiber_organic_cert_no (COLLECT_TO_ARRAY), bale_moisture
   * (DROP).
   */
  private static AttributeInheritanceSchema fiberToYarnSchema() {
    List<InheritanceRule> rules =
        List.of(
            new InheritanceRule(
                "fiber_micronaire",
                "fiber_micronaire",
                InheritanceAction.WEIGHTED_AVERAGE,
                "Weighted average micronaire across consumed fiber batches by quantity."),
            new InheritanceRule(
                "fiber_staple_length",
                "fiber_staple_length",
                InheritanceAction.MIN,
                "Minimum staple length from parent fibers."),
            new InheritanceRule(
                "fiber_grade",
                "raw_fiber_grade",
                InheritanceAction.REQUIRE_EQUAL,
                "All parent fibers must have the same grade."),
            new InheritanceRule(
                "fiber_shade",
                "raw_fiber_shade",
                InheritanceAction.REQUIRE_EQUAL,
                "All parent fibers must have the same shade. Mixing shades is a validation error."),
            new InheritanceRule(
                "fiber_organic_cert_no",
                "fiber_organic_cert_no",
                InheritanceAction.COLLECT_TO_ARRAY,
                "All TC numbers from parent fibers are collected into an array."),
            new InheritanceRule(
                "bale_moisture",
                null,
                InheritanceAction.DROP,
                "Bale moisture is irrelevant at yarn stage."));
    return new AttributeInheritanceSchema(ProductType.FIBER, ProductType.YARN, rules);
  }

  @BeforeEach
  void setUp() {
    when(schemaLoader.getSchema(eq(ProductType.FIBER), eq(ProductType.YARN)))
        .thenReturn(Optional.of(fiberToYarnSchema()));
    engine = new BatchAttributeInheritanceEngine(schemaLoader);

    Map<String, Object> attrsA =
        Map.of(
            "fiber_micronaire",
            4.0,
            "fiber_staple_length",
            30.0,
            "fiber_grade",
            "A",
            "fiber_shade",
            "Cream",
            "fiber_organic_cert_no",
            "TC-1001",
            "bale_moisture",
            8.2);
    Map<String, Object> attrsB =
        Map.of(
            "fiber_micronaire",
            4.5,
            "fiber_staple_length",
            28.0,
            "fiber_grade",
            "A",
            "fiber_shade",
            "Cream",
            "fiber_organic_cert_no",
            "TC-2002",
            "bale_moisture",
            7.9);
    batchA = new BatchAttributes(attrsA, QTY_A);
    batchB = new BatchAttributes(attrsB, QTY_B);
  }

  @Test
  @DisplayName("WEIGHTED_AVERAGE: fiber_micronaire = (600*4.0 + 400*4.5)/1000 = 4.2")
  void weightedAverageMicronaire_isCorrect() {
    Map<String, Object> result =
        engine.resolveInheritedAttributes(
            List.of(batchA, batchB), ProductType.FIBER, ProductType.YARN);

    Object value = result.get("fiber_micronaire");
    assertInstanceOf(BigDecimal.class, value);
    assertEquals(new BigDecimal("4.2000"), value);
  }

  @Test
  @DisplayName("MIN: fiber_staple_length = min(30, 28) = 28")
  void minimumStapleLength_isCorrect() {
    Map<String, Object> result =
        engine.resolveInheritedAttributes(
            List.of(batchA, batchB), ProductType.FIBER, ProductType.YARN);

    Object value = result.get("fiber_staple_length");
    assertInstanceOf(BigDecimal.class, value);
    assertEquals(new BigDecimal("28.0"), value);
  }

  @Test
  @DisplayName("REQUIRE_EQUAL: both batches have grade A → raw_fiber_grade = A")
  void requireEqual_grade_passes() {
    Map<String, Object> result =
        engine.resolveInheritedAttributes(
            List.of(batchA, batchB), ProductType.FIBER, ProductType.YARN);

    assertEquals("A", result.get("raw_fiber_grade"));
  }

  @Test
  @DisplayName("COLLECT_TO_ARRAY: fiber_organic_cert_no = [TC-1001, TC-2002] in any order")
  void collectToArray_certNumbers_isCorrect() {
    Map<String, Object> result =
        engine.resolveInheritedAttributes(
            List.of(batchA, batchB), ProductType.FIBER, ProductType.YARN);

    Object value = result.get("fiber_organic_cert_no");
    assertInstanceOf(List.class, value);
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    assertEquals(2, list.size());
    assertTrue(list.contains("TC-1001"));
    assertTrue(list.contains("TC-2002"));
  }

  @Test
  @DisplayName("DROP: bale_moisture is not present in result")
  void drop_baleMoisture_isAbsent() {
    Map<String, Object> result =
        engine.resolveInheritedAttributes(
            List.of(batchA, batchB), ProductType.FIBER, ProductType.YARN);

    assertFalse(result.containsKey("bale_moisture"));
  }

  @Test
  @DisplayName(
      "REQUIRE_EQUAL conflict: different fiber_shade across parents throws BatchDomainException")
  void requireEqual_shade_conflictThrowsException() {
    BatchAttributes batchC = new BatchAttributes(Map.of("fiber_shade", "White"), QTY_B);

    BatchDomainException ex =
        assertThrows(
            BatchDomainException.class,
            () ->
                engine.resolveInheritedAttributes(
                    List.of(batchA, batchC), ProductType.FIBER, ProductType.YARN));

    assertTrue(
        ex.getMessage().contains("fiber_shade"), "Exception message should mention fiber_shade");
  }
}
