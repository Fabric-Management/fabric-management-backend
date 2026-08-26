package com.fabricmanagement.production.spinning.app;

import com.fabricmanagement.production.core.workorder.app.ProcessSpecsContribution;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.spinning.domain.specs.SpinningProductionSpecs;
import org.springframework.stereotype.Component;

@Component
public final class SpinningProcessSpecsContribution implements ProcessSpecsContribution {

  @Override
  public WorkOrderModuleType type() {
    return WorkOrderModuleType.SPINNING;
  }

  @Override
  public Class<? extends WorkOrderProductionSpecs> specsClass() {
    return SpinningProductionSpecs.class;
  }
}
