package com.fabricmanagement.production.knitting.app;

import com.fabricmanagement.production.core.workorder.app.ProcessSpecsContribution;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.knitting.domain.specs.KnittingProductionSpecs;
import org.springframework.stereotype.Component;

@Component
public final class KnittingProcessSpecsContribution implements ProcessSpecsContribution {

  @Override
  public WorkOrderModuleType type() {
    return WorkOrderModuleType.KNITTING;
  }

  @Override
  public Class<? extends WorkOrderProductionSpecs> specsClass() {
    return KnittingProductionSpecs.class;
  }
}
