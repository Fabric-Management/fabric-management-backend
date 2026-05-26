package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.util.OrderTotals;
import java.time.LocalDate;
import java.util.Map;

/** Command object encapsulating all mutable fields for updating a DRAFT SalesOrder. */
public record SalesOrderUpdateCommand(
    String customerReference,
    LocalDate orderDate,
    LocalDate requestedDeliveryDate,
    LocalDate promisedDeliveryDate,
    OrderTotals totals,
    String shippingAddress,
    String billingAddress,
    String shippingMethod,
    String notes,
    Map<String, Object> metadata,
    ModuleType moduleType,
    LocalDate deadline) {}
