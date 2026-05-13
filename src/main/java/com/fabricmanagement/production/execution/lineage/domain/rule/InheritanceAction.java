package com.fabricmanagement.production.execution.lineage.domain.rule;

/**
 * Defines how a source attribute from a parent batch is transformed when inherited into a child
 * batch's JSONB attributes (Metadata-Driven Attribute Inheritance Engine).
 *
 * <p>Used by {@link
 * com.fabricmanagement.production.execution.lineage.domain.rule.AttributeInheritanceSchema} to
 * describe per-attribute rules for a given source→target product type pair (e.g. FIBER→YARN).
 */
public enum InheritanceAction {

  /**
   * Compute the child value as a weighted average over all consumed parents: sum(sourceValue *
   * consumedQuantity) / sum(consumedQuantity). Typical for numeric metrics (e.g. micronaire,
   * moisture).
   */
  WEIGHTED_AVERAGE,

  /**
   * Take the minimum value of the source attribute across all consumed parents. Typical for "worst
   * case" or lower-bound specs (e.g. minimum strength).
   */
  MIN,

  /**
   * Take the maximum value of the source attribute across all consumed parents. Typical for
   * upper-bound specs or "best case" metrics.
   */
  MAX,

  /**
   * Collect all distinct source values from consumed parents into an array in the child. Typical
   * for categorical or multi-value attributes (e.g. certification codes).
   */
  COLLECT_TO_ARRAY,

  /**
   * Require that the source attribute has the same value in all consumed parents; otherwise
   * inheritance fails or is marked invalid. Child gets that single value. Typical for attributes
   * that must be consistent (e.g. unit, standard).
   */
  REQUIRE_EQUAL,

  /**
   * Do not transfer this attribute to the child. The source attribute is irrelevant for the target
   * product type (e.g. bale moisture when producing yarn). {@code targetAttribute} is null for
   * DROP. Engine must check action == DROP first and skip without reading targetAttribute to avoid
   * NPE.
   */
  DROP,

  /**
   * Copy the source attribute to the child as-is when there is a single parent; behaviour for
   * multiple parents is implementation-defined (e.g. first parent wins). Typical for pass-through
   * identifiers or labels.
   */
  PASS_THROUGH
}
