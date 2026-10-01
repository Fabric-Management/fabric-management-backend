package com.fabricmanagement.sales.salesorder.domain;

import java.time.LocalDate;
import java.util.Map;

/** Command object encapsulating all mutable fields for updating a DRAFT SalesOrder. */
public record SalesOrderUpdateCommand(
    String customerReference,
    LocalDate orderDate,
    LocalDate requestedDeliveryDate,
    DeliveryTerms deliveryTerms,
    DeliveryTermStatus deliveryTermStatus,
    String deliveryContractReference,
    String paymentTerms,
    AgreementContext agreementContext,
    String agreementContextNote,
    String contactName,
    String contactEmail,
    String contactPhone,
    boolean contactWhatsapp,
    String shippingAddress,
    String billingAddress,
    String shippingMethod,
    String notes,
    Map<String, Object> metadata,
    ModuleType derivedModuleType,
    LocalDate deadline) {}
