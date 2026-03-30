package com.fabricmanagement.sales.salesorder.domain.port;

public interface ProductionOrderPort {
  void requestDraftProductionOrder(DraftProductionOrderCommand command);
}
