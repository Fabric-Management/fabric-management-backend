package com.fabricmanagement.iwm.reservation.dto;

import com.fabricmanagement.iwm.reservation.domain.StockReservationStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockReservationResponse {
  private UUID id;
  private UUID salesOrderLineId;
  private UUID locationId;
  private UUID productId;
  private String lotNumber;
  private UUID goodsReceiptItemId;
  private BigDecimal qtyReserved;
  private StockReservationStatus status;
  private OffsetDateTime expiresAt;
  private OffsetDateTime createdAt;
}
