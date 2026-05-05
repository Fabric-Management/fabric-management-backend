package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "specType")
@JsonSubTypes({
  @Type(value = FiberPurchaseSpecs.class, name = "FIBER"),
  @Type(value = YarnPurchaseSpecs.class, name = "YARN"),
  @Type(value = FabricPurchaseSpecs.class, name = "FABRIC"),
  @Type(value = DyePurchaseSpecs.class, name = "DYE_FINISHING"),
  @Type(value = GenericPurchaseSpecs.class, name = "GENERIC")
})
@Schema(
    description = "Module-specific purchase order specs, discriminated by specType",
    oneOf = {
      FiberPurchaseSpecs.class,
      YarnPurchaseSpecs.class,
      FabricPurchaseSpecs.class,
      DyePurchaseSpecs.class,
      GenericPurchaseSpecs.class
    })
public sealed interface PurchaseOrderSpecs
    permits FiberPurchaseSpecs,
        YarnPurchaseSpecs,
        FabricPurchaseSpecs,
        DyePurchaseSpecs,
        GenericPurchaseSpecs {}
