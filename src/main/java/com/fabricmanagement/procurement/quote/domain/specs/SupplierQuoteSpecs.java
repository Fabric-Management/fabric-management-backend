package com.fabricmanagement.procurement.quote.domain.specs;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "specType")
@JsonSubTypes({
  @Type(value = FiberQuoteSpecs.class, name = "FIBER"),
  @Type(value = YarnQuoteSpecs.class, name = "YARN"),
  @Type(value = FabricQuoteSpecs.class, name = "FABRIC"),
  @Type(value = DyeQuoteSpecs.class, name = "DYE_FINISHING"),
  @Type(value = GenericQuoteSpecs.class, name = "GENERIC")
})
@Schema(
    description = "Module-specific quote specs, discriminated by specType",
    oneOf = {
      FiberQuoteSpecs.class,
      YarnQuoteSpecs.class,
      FabricQuoteSpecs.class,
      DyeQuoteSpecs.class,
      GenericQuoteSpecs.class
    })
public sealed interface SupplierQuoteSpecs
    permits FiberQuoteSpecs, YarnQuoteSpecs, FabricQuoteSpecs, DyeQuoteSpecs, GenericQuoteSpecs {}
