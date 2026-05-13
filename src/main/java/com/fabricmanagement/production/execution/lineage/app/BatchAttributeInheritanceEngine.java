package com.fabricmanagement.production.execution.lineage.app;

import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.lineage.domain.port.BatchAttributeInheritancePort;
import com.fabricmanagement.production.execution.lineage.domain.rule.AttributeInheritanceSchema;
import com.fabricmanagement.production.execution.lineage.domain.rule.BatchAttributes;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceAction;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceRule;
import com.fabricmanagement.production.execution.lineage.infra.configuration.AttributeInheritanceSchemaLoader;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Metadata-driven implementation of {@link BatchAttributeInheritancePort} that applies configured
 * inheritance rules (WEIGHTED_AVERAGE, MIN, MAX, COLLECT_TO_ARRAY, REQUIRE_EQUAL, DROP,
 * PASS_THROUGH) to resolve the child batch's JSONB attributes from consumed parent attribute
 * snapshots and quantities.
 */
@Service
public class BatchAttributeInheritanceEngine implements BatchAttributeInheritancePort {

  private static final Logger log = LoggerFactory.getLogger(BatchAttributeInheritanceEngine.class);
  private static final int SCALE = 4;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private final AttributeInheritanceSchemaLoader schemaLoader;

  public BatchAttributeInheritanceEngine(AttributeInheritanceSchemaLoader schemaLoader) {
    this.schemaLoader = schemaLoader;
  }

  @Override
  public Map<String, Object> resolveInheritedAttributes(
      List<BatchAttributes> parentAttributes, ProductType sourceType, ProductType targetType) {
    if (parentAttributes == null) {
      throw new IllegalArgumentException("parentAttributes must not be null");
    }
    if (sourceType == null) {
      throw new IllegalArgumentException("sourceType must not be null");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("targetType must not be null");
    }

    log.info(
        "Resolving inherited attributes: {} parents {} → {}",
        parentAttributes.size(),
        sourceType,
        targetType);

    AttributeInheritanceSchema schema =
        schemaLoader
            .getSchema(sourceType, targetType)
            .orElseThrow(
                () ->
                    new BatchDomainException(
                        "No inheritance schema found",
                        "BATCH_INHERITANCE_SCHEMA_NOT_FOUND",
                        500,
                        new Object[] {sourceType, targetType}));

    Map<String, Object> result = new java.util.HashMap<>();

    for (InheritanceRule rule : schema.rules()) {
      if (rule.action() == InheritanceAction.DROP) {
        if (log.isDebugEnabled()) {
          log.debug("Applying DROP on '{}' → (no target)", rule.sourceAttribute());
        }
        continue;
      }

      if (log.isDebugEnabled()) {
        log.debug(
            "Applying {} on '{}' → '{}'",
            rule.action(),
            rule.sourceAttribute(),
            rule.targetAttribute());
      }

      boolean hasAttribute =
          parentAttributes.stream()
              .anyMatch(parent -> parent.attributes().containsKey(rule.sourceAttribute()));
      if (!hasAttribute) {
        log.warn(
            "Skipping rule: no parent has attribute '{}' (action: {})",
            rule.sourceAttribute(),
            rule.action());
        continue;
      }

      switch (rule.action()) {
        case WEIGHTED_AVERAGE -> applyWeightedAverage(result, rule, parentAttributes);
        case MIN -> applyMinMax(result, rule, parentAttributes, true);
        case MAX -> applyMinMax(result, rule, parentAttributes, false);
        case COLLECT_TO_ARRAY -> applyCollectToArray(result, rule, parentAttributes);
        case REQUIRE_EQUAL -> applyRequireEqual(result, rule, parentAttributes);
        case PASS_THROUGH -> applyPassThrough(result, rule, parentAttributes);
        default -> {
          /* DROP and any other action not applied here */
        }
      }
    }

    return result;
  }

  private void applyWeightedAverage(
      Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    BigDecimal sumProduct = BigDecimal.ZERO;
    BigDecimal sumQty = BigDecimal.ZERO;
    for (BatchAttributes parent : parents) {
      Object raw = parent.attributes().get(rule.sourceAttribute());
      if (raw == null) continue;
      BigDecimal value = toBigDecimal(raw);
      BigDecimal qty = parent.quantity();
      sumProduct = sumProduct.add(value.multiply(qty));
      sumQty = sumQty.add(qty);
    }
    if (sumQty.signum() == 0) {
      log.warn(
          "Skipping rule: total quantity is zero for WEIGHTED_AVERAGE on '{}'",
          rule.sourceAttribute());
      return;
    }
    BigDecimal avg = sumProduct.divide(sumQty, SCALE, ROUNDING_MODE);
    result.put(rule.targetAttribute(), avg);
  }

  private void applyMinMax(
      Map<String, Object> result,
      InheritanceRule rule,
      List<BatchAttributes> parents,
      boolean useMin) {
    Optional<BigDecimal> value =
        parents.stream()
            .map(parent -> parent.attributes().get(rule.sourceAttribute()))
            .filter(Objects::nonNull)
            .map(this::toBigDecimal)
            .reduce(
                useMin
                    ? BinaryOperator.minBy(BigDecimal::compareTo)
                    : BinaryOperator.maxBy(BigDecimal::compareTo));
    value.ifPresent(v -> result.put(rule.targetAttribute(), v));
  }

  private void applyCollectToArray(
      Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    Set<Object> seen = new LinkedHashSet<>();
    List<Object> list = new ArrayList<>();
    for (BatchAttributes parent : parents) {
      Object value = parent.attributes().get(rule.sourceAttribute());
      if (value != null && seen.add(value)) {
        list.add(value);
      }
    }
    if (!list.isEmpty()) {
      result.put(rule.targetAttribute(), list);
    }
  }

  private void applyRequireEqual(
      Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    applySingleValueOrConflict(result, rule, parents);
  }

  private void applyPassThrough(
      Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    applySingleValueOrConflict(result, rule, parents);
  }

  /**
   * Puts the source attribute value into the result if all parents have the same value; otherwise
   * throws BatchDomainException (for REQUIRE_EQUAL / PASS_THROUGH).
   *
   * <p>Uses {@link Object#equals(Object)} for distinctness. For numeric attributes, values that
   * differ only by scale (e.g. 4.0 vs 4.00) may be considered different; future enhancement could
   * normalize numerics before comparison.
   */
  private void applySingleValueOrConflict(
      Map<String, Object> result, InheritanceRule rule, List<BatchAttributes> parents) {
    List<Object> distinct =
        parents.stream()
            .map(parent -> parent.attributes().get(rule.sourceAttribute()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (distinct.isEmpty()) return;
    if (distinct.size() == 1) {
      result.put(rule.targetAttribute(), distinct.get(0));
      return;
    }
    throw new BatchDomainException(
        "Attribute inheritance conflict: attribute '"
            + rule.sourceAttribute()
            + "' has inconsistent values across parent batches: "
            + distinct);
  }

  /**
   * Safe numeric extraction: values in the map may be Double, Integer, Float, or BigDecimal.
   * Non-numeric types throw IllegalArgumentException.
   */
  private BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number num) {
      return BigDecimal.valueOf(num.doubleValue());
    }
    throw new BatchDomainException(
        "Attribute value is not numeric",
        "BATCH_INHERITANCE_NON_NUMERIC_VALUE",
        400,
        new Object[] {value});
  }
}
