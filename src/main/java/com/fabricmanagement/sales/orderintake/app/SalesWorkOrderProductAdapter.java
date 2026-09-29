package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.workorder.api.WorkOrderSalesProductPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesWorkOrderProductAdapter implements WorkOrderSalesProductPort {
  private final SalesOrderLineRepository lines;

  @Override
  public boolean matchesCurrentProduct(UUID tenantId, UUID lineId, UUID outputProductId) {
    return lines
        .findByTenantIdAndId(tenantId, lineId)
        .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
        .map(line -> Objects.equals(line.getProductId(), outputProductId))
        .orElse(false);
  }
}
