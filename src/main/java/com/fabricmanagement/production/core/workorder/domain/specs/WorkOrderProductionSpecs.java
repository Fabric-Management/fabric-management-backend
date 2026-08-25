package com.fabricmanagement.production.core.workorder.domain.specs;

import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic production specifications for WorkOrders.
 *
 * <p>Uses Jackson annotations for JSONB serialization/deserialization. Each specific production
 * process (spinning, weaving, etc.) implements this interface with its specific machine/process
 * parameters.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "specType")
public interface WorkOrderProductionSpecs {

  WorkOrderModuleType specType();
}
