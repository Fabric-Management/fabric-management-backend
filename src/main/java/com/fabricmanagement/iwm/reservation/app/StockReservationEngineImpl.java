package com.fabricmanagement.iwm.reservation.app;

import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import com.fabricmanagement.iwm.reservation.dto.LotSuggestion;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * FIFO bazlı stok lot önerici motor.
 *
 * <p><b>Şema Bağımlılık Notu (CR-10-02):</b> Bu sorgu {@code
 * production.production_execution_inventory_balance} ve {@code
 * production.production_execution_batch} tablolarına doğrudan bağımlıdır. İleride bu tablolar IWM
 * şemasına taşınırsa buradaki SQL güncellenmeli veya bir DB View üzerinden soyutlanmalıdır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationEngineImpl implements StockReservationEngine {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public List<LotSuggestion> suggestLotsFifo(
      UUID tenantId, UUID productId, BigDecimal requiredQty) {
    if (requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("Required quantity must be positive");
    }
    if (tenantId == null || productId == null) {
      throw new IwmDomainException("tenantId and productId must not be null");
    }

    String sql =
        """
        SELECT
            ib.batch_id,
            b.batch_code as lot_number,
            ib.location_id,
            (ib.quantity - ib.reserved_quantity - ib.consumed_quantity - ib.waste_quantity) as available_qty,
            COALESCE(b.production_date, b.created_at) as prod_date
        FROM production.production_execution_inventory_balance ib
        JOIN production.production_execution_batch b ON ib.batch_id = b.id
        WHERE b.product_id = ?
          AND ib.tenant_id = ?
          AND ib.is_active = TRUE
          AND b.is_active = TRUE
          AND ib.deleted_at IS NULL
          AND b.deleted_at IS NULL
          AND (ib.quantity - ib.reserved_quantity - ib.consumed_quantity - ib.waste_quantity) > 0
        ORDER BY COALESCE(b.production_date, b.created_at) ASC
        """;

    List<LotSuggestion> allAvailable =
        jdbcTemplate.query(
            sql,
            new RowMapper<LotSuggestion>() {
              @Override
              public LotSuggestion mapRow(ResultSet rs, int rowNum) throws SQLException {
                java.sql.Timestamp prodTs = rs.getTimestamp("prod_date");
                OffsetDateTime prodDate =
                    prodTs != null ? prodTs.toInstant().atOffset(ZoneOffset.UTC) : null;
                return LotSuggestion.builder()
                    .batchId(UUID.fromString(rs.getString("batch_id")))
                    .lotNumber(rs.getString("lot_number"))
                    .locationId(
                        rs.getString("location_id") != null
                            ? UUID.fromString(rs.getString("location_id"))
                            : null)
                    .availableQty(rs.getBigDecimal("available_qty"))
                    .productionDate(prodDate)
                    .build();
              }
            },
            productId,
            tenantId);

    List<LotSuggestion> suggestions = new ArrayList<>();
    BigDecimal accumulated = BigDecimal.ZERO;

    for (LotSuggestion lot : allAvailable) {
      if (accumulated.compareTo(requiredQty) >= 0) {
        break;
      }
      suggestions.add(lot);
      accumulated = accumulated.add(lot.getAvailableQty());
    }

    if (accumulated.compareTo(requiredQty) < 0) {
      log.warn(
          "Insufficient stock for product {}. Required: {}, Available: {}",
          productId,
          requiredQty,
          accumulated);
    }

    return suggestions;
  }
}
