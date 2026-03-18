package com.fabricmanagement.order.sales.domain;

/**
 * Production module type of a SalesOrder or SalesOrderLine.
 *
 * <p>Determines which {@code moduleSpecs} JSONB schema applies and which recipe pool to search.
 *
 * <ul>
 *   <li>FIBER — raw fiber material
 *   <li>YARN — spun yarn
 *   <li>FABRIC — woven or knitted fabric
 *   <li>DYE_FINISHING — dyeing & finishing services
 * </ul>
 */
public enum ModuleType {
  FIBER,
  YARN,
  FABRIC,
  DYE_FINISHING
}
