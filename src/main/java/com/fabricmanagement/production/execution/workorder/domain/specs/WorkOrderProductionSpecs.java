package com.fabricmanagement.production.execution.workorder.domain.specs;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic production specifications for WorkOrders.
 *
 * <p>Uses Jackson annotations for JSONB serialization/deserialization. Each specific production
 * process (spinning, weaving, etc.) implements this interface with its specific machine/process
 * parameters.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "specType")
@JsonSubTypes({
  @Type(value = SpinningProductionSpecs.class, name = "SPINNING"),
  @Type(value = WeavingProductionSpecs.class, name = "WEAVING"),
  @Type(value = KnittingProductionSpecs.class, name = "KNITTING"),
  @Type(value = DyeingProductionSpecs.class, name = "DYEING"),
  @Type(value = FinishingProductionSpecs.class, name = "FINISHING"),
  @Type(value = GenericProductionSpecs.class, name = "GENERIC")
})
public sealed interface WorkOrderProductionSpecs
    permits SpinningProductionSpecs,
        WeavingProductionSpecs,
        KnittingProductionSpecs,
        DyeingProductionSpecs,
        FinishingProductionSpecs,
        GenericProductionSpecs {

  default WorkOrderModuleType specType() {
    return switch (this) {
      case SpinningProductionSpecs s -> WorkOrderModuleType.SPINNING;
      case WeavingProductionSpecs w -> WorkOrderModuleType.WEAVING;
      case KnittingProductionSpecs k -> WorkOrderModuleType.KNITTING;
      case DyeingProductionSpecs d -> WorkOrderModuleType.DYEING;
      case FinishingProductionSpecs f -> WorkOrderModuleType.FINISHING;
      case GenericProductionSpecs g -> WorkOrderModuleType.GENERIC;
    };
  }
}
