package com.fabricmanagement.production.execution.lineage.domain.rule;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.util.List;

/**
 * Root configuration for the Metadata-Driven Attribute Inheritance Engine: defines how JSONB
 * attributes from consumed parent batches are transformed when producing a child batch for a given
 * source→target material type pair (e.g. FIBER→YARN).
 *
 * <p>Instances are typically deserialized from JSON (e.g. from a config file or database) and used
 * by the engine to resolve {@link
 * com.fabricmanagement.production.execution.lineage.domain.port.BatchAttributeInheritancePort#resolveInheritedAttributes}.
 * Each rule in {@link #rules()} describes one source attribute's handling ({@link
 * InheritanceAction} and optional target key).
 *
 * <p>Example JSON:
 *
 * <pre>
 * {
 *   "sourceType": "FIBER",
 *   "targetType": "YARN",
 *   "rules": [
 *     { "sourceAttribute": "micronaire", "targetAttribute": "fiberMicronaire",
 *       "action": "WEIGHTED_AVERAGE", "description": "..." },
 *     { "sourceAttribute": "baleMoisture", "targetAttribute": null,
 *       "action": "DROP", "description": "..." }
 *   ]
 * }
 * </pre>
 *
 * @param sourceType material type of the consumed parent batch(es) (e.g. FIBER)
 * @param targetType material type of the batch being produced (e.g. YARN)
 * @param rules ordered list of per-attribute inheritance rules; applied in order by the engine
 */
public record AttributeInheritanceSchema(
    MaterialType sourceType, MaterialType targetType, List<InheritanceRule> rules) {}
