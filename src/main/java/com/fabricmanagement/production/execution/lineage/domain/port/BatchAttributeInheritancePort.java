package com.fabricmanagement.production.execution.lineage.domain.port;

import com.fabricmanagement.production.execution.lineage.domain.rule.BatchAttributes;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.List;
import java.util.Map;

/**
 * Port for resolving inherited JSONB attributes when a new (child) batch is produced from one or
 * more consumed parent batches (e.g. Fiber → Yarn spinning, or multiple Fiber lots blended into one
 * Yarn batch).
 *
 * <p>Used by the attribute inheritance engine when recording lineage (parent→child): the child
 * batch's {@code attributes} column is derived from parent snapshots according to configured rules
 * (weighted average, min, require-equal, collect-to-array, drop, etc.). Implementations are
 * product-type and business-rule specific (e.g. Yarn module implements for FIBER→YARN).
 *
 * <p>Fiber module may provide a no-op or identity implementation; Yarn and Fabric modules implement
 * the actual rules (e.g. micronaire weighted by quantity, twist direction for yarn).
 *
 * @see com.fabricmanagement.production.execution.lineage.domain.BatchLineage
 * @see com.fabricmanagement.production.execution.lineage.domain.rule.BatchAttributes
 */
public interface BatchAttributeInheritancePort {

  /**
   * Resolves the JSONB {@code attributes} map for a new (child) batch produced from the given
   * parent attribute snapshots and consumed quantities.
   *
   * <p><b>Parameters:</b>
   *
   * <ul>
   *   <li>{@code parentAttributes} — one entry per consumed parent batch: its attribute snapshot
   *       and the quantity consumed in this production step. Used for weighted average and
   *       multi-parent rules (e.g. REQUIRE_EQUAL, COLLECT_TO_ARRAY). Never null; may be empty
   *       (implementations typically return an empty or default map).
   *   <li>{@code sourceType} — product type of the consumed parent batch(es) (e.g. FIBER, YARN).
   *       Together with {@code targetType} determines which inheritance schema applies (e.g.
   *       FIBER→YARN, YARN→FABRIC). Never null.
   *   <li>{@code targetType} — product type of the batch being produced (e.g. YARN, FABRIC). Drives
   *       which inheritance schema/rules apply. Never null.
   * </ul>
   *
   * <p><b>Return value:</b> The map to persist in the child batch's {@code attributes} JSONB
   * column. Never null. Implementations should return a mutable map (e.g. {@code new HashMap}) so
   * callers can add further fields if needed. Keys follow the convention used by the schema (e.g.
   * {@code fiber_micronaire}, {@code raw_fiber_grade}); values are primitives, strings, or
   * collections as defined by the rules.
   *
   * <p><b>Exceptions:</b> Implementations may throw:
   *
   * <ul>
   *   <li>{@link IllegalArgumentException} — if any of {@code parentAttributes}, {@code
   *       sourceType}, or {@code targetType} is null, or if any element in {@code parentAttributes}
   *       is invalid.
   *   <li>{@link IllegalStateException} — if no inheritance schema is configured for the given
   *       source→target pair.
   *   <li>Domain-specific exceptions (e.g. extending {@link
   *       com.fabricmanagement.production.common.exception.ProductionDomainException}) — when a
   *       business rule fails (e.g. REQUIRE_EQUAL violated because parent attributes differ).
   * </ul>
   *
   * @param parentAttributes list of parent batches' attribute snapshots and consumed quantities;
   *     never null, may be empty
   * @param sourceType product type of the consumed parent batch(es); never null
   * @param targetType product type of the batch being produced; never null
   * @return the attributes map for the child batch's JSONB column; never null
   */
  Map<String, Object> resolveInheritedAttributes(
      List<BatchAttributes> parentAttributes, ProductType sourceType, ProductType targetType);
}
