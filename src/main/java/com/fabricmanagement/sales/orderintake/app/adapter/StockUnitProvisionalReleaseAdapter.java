package com.fabricmanagement.sales.orderintake.app.adapter;

import com.fabricmanagement.production.core.stockunit.api.StockUnitProvisionalReleasePort;
import com.fabricmanagement.sales.orderintake.app.ProvisionalStockLifecycleService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockUnitProvisionalReleaseAdapter implements StockUnitProvisionalReleasePort {
  private final ProvisionalStockLifecycleService lifecycle;

  @Override
  public void releaseOwnerLine(UUID ownerLineId) {
    lifecycle.ownerLine(ownerLineId);
  }
}
