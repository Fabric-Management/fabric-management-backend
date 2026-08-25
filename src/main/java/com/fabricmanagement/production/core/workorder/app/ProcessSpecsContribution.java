package com.fabricmanagement.production.core.workorder.app;

import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;

/** SPI implemented by production process modules to contribute their specification type. */
public interface ProcessSpecsContribution {

  WorkOrderModuleType type();

  Class<? extends WorkOrderProductionSpecs> specsClass();
}
