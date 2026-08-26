package com.fabricmanagement.production.weaving.app;

import com.fabricmanagement.production.core.workorder.app.ProcessSpecsContribution;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.weaving.domain.specs.WeavingProductionSpecs;
import org.springframework.stereotype.Component;

@Component
public final class WeavingProcessSpecsContribution implements ProcessSpecsContribution {

  @Override
  public WorkOrderModuleType type() {
    return WorkOrderModuleType.WEAVING;
  }

  @Override
  public Class<? extends WorkOrderProductionSpecs> specsClass() {
    return WeavingProductionSpecs.class;
  }
}
