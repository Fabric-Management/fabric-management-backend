package com.fabricmanagement.production.execution.stockunit.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService.SubcontractOutputInfo;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException.Reason;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptConfirmedEventListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private StockUnitService stockUnitService;
  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private SubcontractOrderQueryService subcontractOrderQueryService;
  @Mock private IdempotentEventHandler idempotentEventHandler;

  private GoodsReceiptConfirmedEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new GoodsReceiptConfirmedEventListener(
            stockUnitService,
            batchRepository,
            stockUnitRepository,
            subcontractOrderQueryService,
            idempotentEventHandler);
    doAnswer(
            invocation -> {
              invocation.getArgument(3, Runnable.class).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(), any(), anyString(), any());
  }

  @Test
  void purchaseOrderFailsWithPendingReasonAndCompleteContext() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.PURCHASE_ORDER, oneItem());

    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> {
              assertThat(exception.getReason()).isEqualTo(Reason.PO_MATERIALIZATION_PENDING);
              assertThat(exception.getErrorCode())
                  .isEqualTo(StockUnitMaterializationException.ERROR_CODE);
              assertThat(exception.getMessage())
                  .contains(
                      "reason=PO_MATERIALIZATION_PENDING",
                      "receiptNumber=" + event.getReceiptNumber(),
                      "sourceType=PURCHASE_ORDER",
                      "eventId=" + event.getEventId());
            });

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void emptyReceiptFailsClosed() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, List.of());

    assertReason(event, Reason.EMPTY_RECEIPT_ITEMS);

    verify(stockUnitRepository, never())
        .existsByTenantIdAndSourceTypeAndSourceId(any(), any(), any());
  }

  @Test
  void missingSubcontractOutputTypePreservesItsSpecificReason() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.SUBCONTRACT_ORDER, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(subcontractOrderQueryService.getSubcontractOutputInfo(TENANT_ID, event.getSourceId()))
        .thenReturn(new SubcontractOutputInfo("SC-001", UUID.randomUUID(), null, "KG", null));

    assertReason(event, Reason.MISSING_SC_OUTPUT_TYPE);

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void unexpectedBatchFailureIsWrappedAndKeepsItsCause() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(batchRepository.findByIdAndTenantId(event.getSourceId(), TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> {
              assertThat(exception.getReason()).isEqualTo(Reason.PROCESSING_FAILED);
              assertThat(exception.getCause())
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("Batch not found for GR confirm");
            });
  }

  @Test
  void secondaryDuplicateGuardCompletesWithoutCreatingUnits() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            TENANT_ID, StockUnitSourceType.GOODS_RECEIPT, event.getItems().get(0).itemId()))
        .thenReturn(true);

    assertThatCode(() -> listener.onGoodsReceiptConfirmed(event)).doesNotThrowAnyException();

    verify(batchRepository, never()).findByIdAndTenantId(any(), any());
    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void batchReceiptCreatesOneRequestPerItem() {
    List<GoodsReceiptConfirmedEvent.ReceiptItemData> items =
        List.of(item("ROLL-001", "10.5", "11.0"), item("ROLL-002", "12.5", "13.0"));
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, items);
    UUID locationId = UUID.randomUUID();
    Batch batch =
        Batch.builder().productType(ProductType.FABRIC).unit("KG").locationId(locationId).build();
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(batchRepository.findByIdAndTenantId(event.getSourceId(), TENANT_ID))
        .thenReturn(Optional.of(batch));

    listener.onGoodsReceiptConfirmed(event);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockUnitService.CreateStockUnitRequest>> requestsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(stockUnitService)
        .createBulk(eq(event.getSourceId()), requestsCaptor.capture(), any(UUID.class));
    assertThat(requestsCaptor.getValue())
        .extracting(StockUnitService.CreateStockUnitRequest::sourceId)
        .containsExactly(items.get(0).itemId(), items.get(1).itemId());
    assertThat(requestsCaptor.getValue())
        .allSatisfy(
            request -> {
              assertThat(request.productType()).isEqualTo(ProductType.FABRIC);
              assertThat(request.unit()).isEqualTo("KG");
              assertThat(request.locationId()).isEqualTo(locationId);
            });
  }

  private void assertReason(GoodsReceiptConfirmedEvent event, Reason expectedReason) {
    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> assertThat(exception.getReason()).isEqualTo(expectedReason));
  }

  private GoodsReceiptConfirmedEvent event(
      GoodsReceiptSourceType sourceType, List<GoodsReceiptConfirmedEvent.ReceiptItemData> items) {
    return GoodsReceiptConfirmedEvent.builder()
        .tenantId(TENANT_ID)
        .receiptId(UUID.randomUUID())
        .receiptNumber("GR-" + UUID.randomUUID())
        .sourceType(sourceType)
        .sourceId(UUID.randomUUID())
        .items(items)
        .build();
  }

  private List<GoodsReceiptConfirmedEvent.ReceiptItemData> oneItem() {
    return List.of(item("UNIT-001", "10.0", "11.0"));
  }

  private GoodsReceiptConfirmedEvent.ReceiptItemData item(
      String barcode, String netWeight, String grossWeight) {
    return new GoodsReceiptConfirmedEvent.ReceiptItemData(
        UUID.randomUUID(), barcode, new BigDecimal(netWeight), new BigDecimal(grossWeight));
  }
}
