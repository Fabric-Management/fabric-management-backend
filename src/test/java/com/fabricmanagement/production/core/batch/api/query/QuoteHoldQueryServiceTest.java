package com.fabricmanagement.production.core.batch.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.LotIntentView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.QuoteHolds;
import com.fabricmanagement.production.core.batch.app.BatchCommitmentQuantityService;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent;
import com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntentStatus;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchLotQuantityIntentRepository.IntentUnitQuantityRow;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository.HeldPieceRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** STOCK-PREVIEW-1 A2: quote holds on a set of lots, read-only. Data is fictional. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuoteHoldQueryServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final LocalDate EXPIRES = LocalDate.parse("2026-10-21");

  @Mock private BatchRepository batchRepository;
  @Mock private BatchLotQuantityIntentRepository intentRepository;
  @Mock private StockUnitSoftHoldRepository softHoldRepository;
  @Mock private BatchReservationRepository reservationRepository;

  private final BatchPrimaryMeasureService measures = new BatchPrimaryMeasureService();
  private QuoteHoldQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new QuoteHoldQueryService(batchRepository, intentRepository, softHoldRepository, measures);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void intentsAreStatedInTheLotsCanonicalUnit_andAnUnconvertibleOneIsNullNeverZero() {
    Batch fabric = batch("LOT-F", ProductType.FABRIC, "M");
    Batch yarn = batch("LOT-Y", ProductType.YARN, "KG");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(eq(TENANT_ID), any()))
        .thenReturn(List.of(fabric, yarn));
    when(intentRepository.findActiveByBatchIds(eq(TENANT_ID), any(), isNull()))
        .thenReturn(
            List.of(
                intent(fabric, "1500", "CM"),
                intent(fabric, "20", "M"),
                intent(fabric, "7", "KG"),
                intent(yarn, "2500", "G")));
    when(softHoldRepository.findActiveByBatchIds(eq(TENANT_ID), any())).thenReturn(List.of());

    QuoteHolds holds = service.find(List.of(fabric.getId(), yarn.getId()));

    assertThat(holds.intents())
        .extracting(
            view ->
                view.canonicalQuantity() == null
                    ? null
                    : view.canonicalQuantity().stripTrailingZeros().toPlainString())
        .containsExactly("15", "20", null, "2.5");
    LotIntentView first = holds.intents().getFirst();
    assertThat(first.batchId()).isEqualTo(fabric.getId());
    assertThat(first.quoteNumber()).isEqualTo("Q-0001");
    assertThat(first.marketerName()).isEqualTo("Sales Rep");
    assertThat(first.quantity()).isEqualByComparingTo("1500");
    assertThat(first.unit()).isEqualTo("CM");
    assertThat(first.expiresAt()).isEqualTo(EXPIRES);
    verify(intentRepository, never()).save(any());
    verify(softHoldRepository, never()).save(any());
  }

  @Test
  void theConvertibleSumPerLotIsWhatTheQuotePickerReadsAsSoftIntent() {
    Batch fabric = batch("LOT-F", ProductType.FABRIC, "M");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(eq(TENANT_ID), any()))
        .thenReturn(List.of(fabric));
    when(intentRepository.findActiveByBatchIds(eq(TENANT_ID), any(), isNull()))
        .thenReturn(
            List.of(
                intent(fabric, "1500", "CM"),
                intent(fabric, "20", "M"),
                intent(fabric, "5", "m "),
                intent(fabric, "7", "KG")));
    when(softHoldRepository.findActiveByBatchIds(eq(TENANT_ID), any())).thenReturn(List.of());
    // The quote picker's aggregate over the same rows, grouped by normalised unit. The row mocks
    // are stubbed before the repository stubbing starts.
    List<IntentUnitQuantityRow> pickerRows =
        List.of(
            unitRow(fabric, "CM", "1500", 1),
            unitRow(fabric, "M", "25", 2),
            unitRow(fabric, "KG", "7", 1));
    when(intentRepository.sumActiveRowsByBatchIdsAndUnit(
            eq(TENANT_ID), any(), eq(BatchLotQuantityIntentStatus.ACTIVE), isNull()))
        .thenReturn(pickerRows);
    when(reservationRepository.sumRemainingRowsByBatchIdsAndUnit(eq(TENANT_ID), any(), any()))
        .thenReturn(List.of());
    BatchCommitmentQuantityService picker =
        new BatchCommitmentQuantityService(intentRepository, reservationRepository, measures);

    QuoteHolds holds = service.find(List.of(fabric.getId()));
    BigDecimal previewSum =
        holds.intents().stream()
            .map(LotIntentView::canonicalQuantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal pickerSum =
        picker.summarize(TENANT_ID, List.of(fabric), null).get(fabric.getId()).softIntent();

    assertThat(previewSum).isEqualByComparingTo("40");
    assertThat(previewSum).isEqualByComparingTo(pickerSum);
  }

  @Test
  void heldPiecesCarryTheirLotAndQuoteLineOnly() {
    Batch fabric = batch("LOT-F", ProductType.FABRIC, "M");
    UUID stockUnitId = UUID.randomUUID();
    UUID quoteLineId = UUID.randomUUID();
    HeldPieceRow row = mock(HeldPieceRow.class);
    when(row.getStockUnitId()).thenReturn(stockUnitId);
    when(row.getBatchId()).thenReturn(fabric.getId());
    when(row.getQuoteLineId()).thenReturn(quoteLineId);
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(eq(TENANT_ID), any()))
        .thenReturn(List.of(fabric));
    when(intentRepository.findActiveByBatchIds(eq(TENANT_ID), any(), isNull()))
        .thenReturn(List.of());
    when(softHoldRepository.findActiveByBatchIds(eq(TENANT_ID), any())).thenReturn(List.of(row));

    QuoteHolds holds = service.find(List.of(fabric.getId()));

    assertThat(holds.heldPieces())
        .containsExactly(
            new QuoteHoldQueryService.HeldPieceView(stockUnitId, fabric.getId(), quoteLineId));
  }

  @Test
  void noLotsReadNothing() {
    QuoteHolds holds = service.find(List.of());

    assertThat(holds.intents()).isEmpty();
    assertThat(holds.heldPieces()).isEmpty();
    verifyNoInteractions(batchRepository, intentRepository, softHoldRepository);
  }

  private static BatchLotQuantityIntent intent(Batch batch, String quantity, String unit) {
    return BatchLotQuantityIntent.place(
        TENANT_ID,
        UUID.randomUUID(),
        "Q-0001",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Sales Rep",
        batch.getId(),
        new BigDecimal(quantity),
        unit,
        EXPIRES);
  }

  private static IntentUnitQuantityRow unitRow(
      Batch batch, String unit, String quantity, long rows) {
    IntentUnitQuantityRow row = mock(IntentUnitQuantityRow.class);
    when(row.getBatchId()).thenReturn(batch.getId());
    when(row.getUnit()).thenReturn(unit);
    when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
    when(row.getRowCount()).thenReturn(rows);
    return row;
  }

  private static Batch batch(String code, ProductType type, String unit) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(type)
            .batchCode(code)
            .quantity(new BigDecimal("100"))
            .unit(unit)
            .status(BatchStatus.AVAILABLE)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(TENANT_ID);
    batch.setIsActive(true);
    return batch;
  }
}
