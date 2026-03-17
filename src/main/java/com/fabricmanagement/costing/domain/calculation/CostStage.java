package com.fabricmanagement.costing.domain.calculation;

/**
 * The three stages of a cost calculation lifecycle.
 *
 * <ul>
 *   <li>ESTIMATED — computed from PriceList + overhead before any real order is placed (feeds
 *       Quote.estimatedUnitCost and DiscountPolicy margin check)
 *   <li>PLANNED — computed once the WorkOrder is confirmed with real supplier prices and lot
 *       quantities (feeds WorkOrder.plannedCost)
 *   <li>ACTUAL — computed after the Batch is completed with real consumption, waste, and energy
 *       data (feeds Batch.actualCost)
 * </ul>
 */
public enum CostStage {
  ESTIMATED,
  PLANNED,
  ACTUAL
}
