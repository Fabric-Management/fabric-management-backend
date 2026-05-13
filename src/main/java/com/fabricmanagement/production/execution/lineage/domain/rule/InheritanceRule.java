package com.fabricmanagement.production.execution.lineage.domain.rule;

/**
 * A single rule within an {@link AttributeInheritanceSchema}, describing how one source attribute
 * is inherited into the child batch's attributes for a given source→target product type pair.
 *
 * <p>Part of the Metadata-Driven Attribute Inheritance Engine: rules are typically loaded from JSON
 * configuration and applied by the engine when resolving {@link
 * com.fabricmanagement.production.execution.lineage.domain.port.BatchAttributeInheritancePort#resolveInheritedAttributes}.
 *
 * @param sourceAttribute key of the attribute in the parent batch's JSONB {@code attributes} map
 * @param targetAttribute key to write in the child batch's JSONB {@code attributes} map; may be
 *     null for {@link InheritanceAction#DROP} (attribute is not transferred)
 * @param action how to compute or transfer the value (e.g. WEIGHTED_AVERAGE, DROP)
 * @param description human-readable explanation of the rule for documentation and tooling
 *     <p><b>Implementation note for Engine:</b> When iterating rules, handle {@link
 *     InheritanceAction#DROP} first and skip any use of {@code targetAttribute} for that rule.
 *     Example: {@code if (rule.action() == DROP) continue; } Otherwise a null {@code
 *     targetAttribute} can cause NPE when the engine writes to the child map.
 */
public record InheritanceRule(
    String sourceAttribute, String targetAttribute, InheritanceAction action, String description) {}
