package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.flowboard.generator.domain.port.out.StockQueryPort;
import com.fabricmanagement.production.execution.inventory.api.facade.InventoryFacade;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation of StockQueryPort. Integrates with the IWM module to query real-time stock
 * balances.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StockQueryAdapter implements StockQueryPort {

  private final InventoryFacade inventoryFacade;

  @Override
  public BigDecimal getAvailableStockByProduct(UUID tenantId, UUID productId) {
    return inventoryFacade.getAvailableQuantityByProduct(tenantId, productId);
  }
}
