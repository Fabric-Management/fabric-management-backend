package com.fabricmanagement.flowboard.task.domain;

/** Task types used by manual work and event-driven Task generation. */
public enum TaskType {
  /** Assign a recipe or prepare production work. */
  PLANNING,
  /** Start and manage production. */
  PRODUCTION,
  /** Perform a quality check. */
  QUALITY,
  /** Place or manage warehouse stock. */
  WAREHOUSE,
  /** Prepare or dispatch a shipment. */
  SHIPMENT,
  /** Review an operation awaiting approval. */
  APPROVAL,
  /** Select a recipe manually. */
  RECIPE_ASSIGNMENT,
  /** Source material from a supplier. */
  PROCUREMENT,
  /** Review a costing variance. */
  COSTING,
  /** Prepare or send a sample. */
  SAMPLE,
  /** Process a return. */
  RETURN,
  /** Perform a stock count. */
  STOCK_COUNT,
  /** Perform maintenance. */
  MAINTENANCE,
  /** Decide how a confirmed SalesOrder is covered. */
  ORDER_COVER,
  /** General work without a narrower type. */
  GENERAL
}
