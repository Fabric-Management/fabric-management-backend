package com.fabricmanagement.procurement.rfq.domain.specs;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "specType")
@JsonSubTypes({
  @Type(value = FiberRFQSpecs.class, name = "FIBER"),
  @Type(value = YarnRFQSpecs.class, name = "YARN"),
  @Type(value = FabricRFQSpecs.class, name = "FABRIC"),
  @Type(value = DyeRFQSpecs.class, name = "DYE_FINISHING"),
  @Type(value = GenericRFQSpecs.class, name = "GENERIC")
})
@Schema(
    description = "Module-specific RFQ specs, discriminated by specType",
    oneOf = {
      FiberRFQSpecs.class,
      YarnRFQSpecs.class,
      FabricRFQSpecs.class,
      DyeRFQSpecs.class,
      GenericRFQSpecs.class
    })
public sealed interface SupplierRFQSpecs
    permits FiberRFQSpecs, YarnRFQSpecs, FabricRFQSpecs, DyeRFQSpecs, GenericRFQSpecs {}
