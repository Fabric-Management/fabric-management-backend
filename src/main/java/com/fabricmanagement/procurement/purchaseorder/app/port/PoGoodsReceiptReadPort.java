package com.fabricmanagement.procurement.purchaseorder.app.port;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Consumer-owned read port for PO receipt coverage. */
public interface PoGoodsReceiptReadPort {

  PoReceiptTotals sumReceivedByLine(
      UUID tenantId, UUID purchaseOrderId, Map<UUID, String> lineUnits);

  record PoReceiptTotals(boolean hasConfirmedReceipts, Map<UUID, LineReceiptTotal> receivedByLine) {

    public PoReceiptTotals {
      receivedByLine = Map.copyOf(receivedByLine);
    }
  }

  record LineReceiptTotal(BigDecimal receivedQty, int excludedItemCount, boolean unitSupported) {

    public LineReceiptTotal {
      receivedQty = receivedQty != null ? receivedQty : BigDecimal.ZERO;
    }

    public boolean receiveMismatch() {
      return !unitSupported || excludedItemCount > 0;
    }
  }
}
