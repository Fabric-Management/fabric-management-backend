package com.fabricmanagement.costing.domain.calculation;

/**
 * Discriminator for the polymorphic {@code entityId} in {@link CostCalculation}.
 *
 * <ul>
 *   <li>QUOTE — the entityId is a Quote.id (ESTIMATED stage)
 *   <li>WORK_ORDER — the entityId is a WorkOrder.id (PLANNED stage)
 *   <li>BATCH — the entityId is a Batch.id (ACTUAL stage)
 * </ul>
 */
public enum CostEntityType {
  QUOTE,
  WORK_ORDER,
  BATCH
}
