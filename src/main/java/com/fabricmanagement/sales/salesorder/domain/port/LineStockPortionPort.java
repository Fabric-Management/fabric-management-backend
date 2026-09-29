package com.fabricmanagement.sales.salesorder.domain.port;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * The finished stock held for a line since its confirmation, in the line unit (SOI D5, K10). Empty
 * means it cannot be stated in the line unit; zero means nothing is held.
 */
@FunctionalInterface
public interface LineStockPortionPort {
  Optional<BigDecimal> ownFinishedStock(SalesOrderLine line);
}
