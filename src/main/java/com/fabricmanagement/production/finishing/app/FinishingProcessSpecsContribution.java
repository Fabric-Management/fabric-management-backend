package com.fabricmanagement.production.finishing.app;

import com.fabricmanagement.production.core.workorder.app.ProcessSpecsContribution;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.finishing.domain.specs.FinishingProductionSpecs;
import org.springframework.stereotype.Component;

@Component
public final class FinishingProcessSpecsContribution implements ProcessSpecsContribution {

  @Override
  public WorkOrderModuleType type() {
    return WorkOrderModuleType.FINISHING;
  }

  @Override
  public Class<? extends WorkOrderProductionSpecs> specsClass() {
    return FinishingProductionSpecs.class;
  }
}
