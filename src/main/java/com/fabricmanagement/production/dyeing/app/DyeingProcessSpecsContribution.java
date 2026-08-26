package com.fabricmanagement.production.dyeing.app;

import com.fabricmanagement.production.core.workorder.app.ProcessSpecsContribution;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.dyeing.domain.specs.DyeingProductionSpecs;
import org.springframework.stereotype.Component;

@Component
public final class DyeingProcessSpecsContribution implements ProcessSpecsContribution {

  @Override
  public WorkOrderModuleType type() {
    return WorkOrderModuleType.DYEING;
  }

  @Override
  public Class<? extends WorkOrderProductionSpecs> specsClass() {
    return DyeingProductionSpecs.class;
  }
}
