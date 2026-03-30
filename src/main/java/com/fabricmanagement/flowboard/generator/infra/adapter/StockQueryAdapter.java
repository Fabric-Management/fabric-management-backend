package com.fabricmanagement.flowboard.generator.infra.adapter;

import com.fabricmanagement.flowboard.generator.domain.port.out.StockQueryPort;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation of StockQueryPort. Integrates with the IWM module to query real-time stock
 * balances.
 */
@Component
@Slf4j
public class StockQueryAdapter implements StockQueryPort {

  @Override
  public BigDecimal getAvailableStockForOrder(UUID tenantId, UUID salesOrderId) {
    // TODO: Integrate with actual IwmStockFacade to retrieve real stock balance
    log.debug("Stub: queried stock for sales order {} in tenant {}", salesOrderId, tenantId);
    return BigDecimal.ZERO;
  }
}
