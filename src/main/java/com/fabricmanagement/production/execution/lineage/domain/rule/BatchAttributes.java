package com.fabricmanagement.production.execution.lineage.domain.rule;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Input unit for the attribute inheritance engine: one parent batch's attribute map paired with its
 * consumed quantity.
 *
 * <p>When resolving inherited attributes for a child batch (e.g. Yarn produced from consumed Fiber
 * batches), the engine receives a list of {@code BatchAttributes} — one per consumed parent. The
 * {@link #quantity()} is used for {@link InheritanceAction#WEIGHTED_AVERAGE} and similar rules so
 * that numeric attributes (e.g. micronaire, staple length) can be computed as quantity-weighted
 * values across parents.
 *
 * <p>{@code attributes} is never null (null is normalized to an empty map). A defensive copy is
 * stored so that later changes to the caller's map do not affect this record. {@code quantity} must
 * be non-null and non-negative.
 *
 * @param attributes parent batch's JSONB attributes snapshot; never null (null is replaced with
 *     {@link java.util.Collections#emptyMap()}; non-null is copied defensively)
 * @param quantity quantity consumed from this parent in the production step; must be non-null and
 *     &gt;= 0 (used for weighted average and validation)
 * @see com.fabricmanagement.production.execution.lineage.domain.port.BatchAttributeInheritancePort
 */
public record BatchAttributes(Map<String, Object> attributes, BigDecimal quantity) {

  public BatchAttributes {
    attributes = attributes == null ? Collections.emptyMap() : new HashMap<>(attributes);
    if (quantity == null || quantity.signum() < 0) {
      throw new IllegalArgumentException(
          "quantity must be non-null and non-negative, but was: " + quantity);
    }
  }
}
