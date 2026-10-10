package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.orderintake.app.ConfirmationGate;
import com.fabricmanagement.sales.salesorder.domain.CatalogLineInput;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The order-intake rules the classic sales-order write paths must honour (SOI). {@link
 * SalesOrderService} calls only this seam, so create, confirmation and cancellation share one
 * implementation of the intake rules instead of each carrying its own copy.
 */
@Component
@RequiredArgsConstructor
public class OrderIntakeHooks {

  static final String CANCELLATION_RELEASE = "ORDER_CANCELLED";

  private final CatalogLineValidator catalogLineValidator;
  private final ConfirmationGate confirmationGate;

  /** Catalogue rules over every active line the order will hold after the write (SOI R02–R06). */
  public void validateLines(
      UUID tenantId, UUID customerId, Collection<? extends CatalogLineInput> lines) {
    catalogLineValidator.validate(tenantId, customerId, lines);
  }

  /** Refuses a confirmation or an approval request the intake rules block; changes nothing. */
  public void checkConfirmable(SalesOrder order, List<SalesOrderLine> lines) {
    List<ConfirmationGate.Block> blocks = confirmationGate.blocks(order, lines);
    if (!blocks.isEmpty()) {
      throw ConfirmationGate.toException(blocks.getFirst());
    }
  }

  /** The intake blocks as readable codes; empty when the order may be confirmed. */
  public List<String> confirmationBlockers(SalesOrder order, List<SalesOrderLine> lines) {
    return confirmationGate.blocks(order, lines).stream()
        .map(block -> block.lineId() == null ? block.code() : block.code() + "@" + block.lineId())
        .toList();
  }

  /** Re-checks and holds the accepted pieces in the confirmation transaction (SOI A05). */
  public void allocateAtConfirmation(SalesOrder order, List<SalesOrderLine> lines, UUID actor) {
    confirmationGate.allocate(order, lines, actor != null ? actor : SystemUser.ID);
  }

  /** A cancelled order gives its held pieces back. */
  public void releaseOnCancellation(Collection<UUID> lineIds, UUID actor) {
    confirmationGate.release(lineIds, actor != null ? actor : SystemUser.ID, CANCELLATION_RELEASE);
  }
}
