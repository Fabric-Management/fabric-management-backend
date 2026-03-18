package com.fabricmanagement.costing.domain.item;

/**
 * Defines how a cost item applies to the production quantity.
 *
 * <ul>
 *   <li>PER_KG — unit cost times kilogram quantity
 *   <li>PER_HOUR — unit cost times machine/labor hours
 *   <li>PER_UNIT — unit cost times unit count (bobbins, bales, etc.)
 *   <li>PERCENTAGE — percentage of the total calculated so far (e.g. overhead 12%)
 *   <li>FIXED — a flat cost per batch regardless of quantity
 * </ul>
 */
public enum CalculationBase {
  PER_KG,
  PER_HOUR,
  PER_UNIT,
  PERCENTAGE,
  FIXED
}
