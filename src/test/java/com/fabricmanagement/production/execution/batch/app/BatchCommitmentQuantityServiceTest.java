package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntentStatus;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.BatchUnitMismatchSource;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchCommitmentQuantityServiceTest {

  @Test
  void canonicalisesCompatibleRowsAndExcludesDimensionMismatches() {
    UUID tenantId = UUID.randomUUID();
    Batch batch = batch(tenantId);
    var intents = mock(BatchLotQuantityIntentRepository.class);
    var reservations = mock(BatchReservationRepository.class);
    var service =
        new BatchCommitmentQuantityService(intents, reservations, new BatchPrimaryMeasureService());
    var matchingIntent = intentRow(batch.getId(), "M", "10", 1);
    var wrongIntent = intentRow(batch.getId(), "KG", "5", 2);
    var matchingReservation = reservationRow(batch.getId(), "CM", "100", 1);
    when(intents.sumActiveRowsByBatchIdsAndUnit(
            tenantId, List.of(batch.getId()), BatchLotQuantityIntentStatus.ACTIVE, null))
        .thenReturn(List.of(matchingIntent, wrongIntent));
    when(reservations.sumRemainingRowsByBatchIdsAndUnit(
            tenantId,
            List.of(batch.getId()),
            List.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED)))
        .thenReturn(List.of(matchingReservation));

    var result = service.summarize(tenantId, List.of(batch), null).get(batch.getId());

    assertThat(result.softIntent()).isEqualByComparingTo("10");
    assertThat(result.hardReserved()).isEqualByComparingTo("1");
    assertThat(result.unitMismatches())
        .singleElement()
        .satisfies(
            mismatch -> {
              assertThat(mismatch.source()).isEqualTo(BatchUnitMismatchSource.SOFT_INTENT);
              assertThat(mismatch.unit()).isEqualTo("KG");
              assertThat(mismatch.quantity()).isEqualByComparingTo("5");
              assertThat(mismatch.rowCount()).isEqualTo(2);
            });
  }

  private Batch batch(UUID tenantId) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .batchCode("LOT-001")
            .quantity(new BigDecimal("100"))
            .consumedQuantity(BigDecimal.ZERO)
            .reservedQuantity(BigDecimal.ZERO)
            .unit("M")
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(tenantId);
    batch.setIsActive(true);
    return batch;
  }

  private BatchLotQuantityIntentRepository.IntentUnitQuantityRow intentRow(
      UUID batchId, String unit, String quantity, long count) {
    var row = mock(BatchLotQuantityIntentRepository.IntentUnitQuantityRow.class);
    when(row.getBatchId()).thenReturn(batchId);
    when(row.getUnit()).thenReturn(unit);
    when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
    when(row.getRowCount()).thenReturn(count);
    return row;
  }

  private BatchReservationRepository.ReservationUnitQuantityRow reservationRow(
      UUID batchId, String unit, String quantity, long count) {
    var row = mock(BatchReservationRepository.ReservationUnitQuantityRow.class);
    when(row.getBatchId()).thenReturn(batchId);
    when(row.getUnit()).thenReturn(unit);
    when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
    when(row.getRowCount()).thenReturn(count);
    return row;
  }
}
