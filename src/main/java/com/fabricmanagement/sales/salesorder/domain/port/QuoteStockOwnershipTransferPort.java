package com.fabricmanagement.sales.salesorder.domain.port;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.UUID;

/**
 * Synchronous ownership transfer joins create; a bare quote-id event cannot infer line mappings.
 */
public interface QuoteStockOwnershipTransferPort {
  void prepare(SalesOrder order, UUID actor);

  void prepareLine(SalesOrder order, SalesOrderLine target, UUID sourceQuoteLineId, UUID actor);

  void transfer(SalesOrder order, SalesOrderLine target, UUID sourceQuoteLineId, UUID actor);
}
