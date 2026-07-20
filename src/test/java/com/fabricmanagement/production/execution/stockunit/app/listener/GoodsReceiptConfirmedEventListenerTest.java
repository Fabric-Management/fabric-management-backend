package com.fabricmanagement.production.execution.stockunit.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.procurement.purchaseorder.api.query.PurchaseOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService.SubcontractOutputInfo;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitMaterializationException.Reason;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptConfirmedEventListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private StockUnitService stockUnitService;
  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private SubcontractOrderQueryService subcontractOrderQueryService;
  @Mock private PurchaseOrderQueryService purchaseOrderQueryService;
  @Mock private ProductFacade productFacade;
  @Mock private ApplicationEventPublisher eventPublisher;
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
            purchaseOrderQueryService,
            productFacade,
            new BatchPrimaryMeasureService(),
            eventPublisher,
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
  void stalePurchaseOrderEventFailsWithPredatesContractReasonAndCompleteContext() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.PURCHASE_ORDER, oneItem());

    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> {
              assertThat(exception.getReason()).isEqualTo(Reason.PO_EVENT_PREDATES_CONTRACT);
              assertThat(exception.getErrorCode())
                  .isEqualTo(StockUnitMaterializationException.ERROR_CODE);
              assertThat(exception.getMessage())
                  .contains(
                      "reason=PO_EVENT_PREDATES_CONTRACT",
                      "receiptNumber=" + event.getReceiptNumber(),
                      "sourceType=PURCHASE_ORDER",
                      "eventId=" + event.getEventId());
            });

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void fabricPurchaseReceiptCreatesCanonicalBatchAndLengthBearingUnits() {
    UUID lineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    GoodsReceiptConfirmedEvent event =
        purchaseEvent(
            lineId,
            List.of(
                item("ROLL-001", "10.5", "11.0", "2.5", "M"),
                item("ROLL-002", "12.5", "13.0", "150", "CM")));
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, event.getSourceId(), lineId))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-001", productId));
    when(productFacade.findById(TENANT_ID, productId))
        .thenReturn(Optional.of(ProductDto.builder().productType(ProductType.FABRIC).build()));
    when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
            TENANT_ID, event.getReceiptId(), BatchSourceType.PURCHASE))
        .thenReturn(Optional.empty());
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    listener.onGoodsReceiptConfirmed(event);

    ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
    verify(batchRepository).save(batchCaptor.capture());
    Batch batch = batchCaptor.getValue();
    assertThat(batch.getBatchCode()).isEqualTo("PUR-2026-ABC12345");
    assertThat(batch.getSupplierBatchCode()).isEqualTo("SUP-LOT-42");
    assertThat(batch.getUnit()).isEqualTo("M");
    assertThat(batch.getQuantity()).isEqualByComparingTo("4.0");
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_QC);
    assertThat(batch.getColorId()).isNull();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockUnitService.CreateStockUnitRequest>> requestsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(stockUnitService).createBulk(any(), requestsCaptor.capture(), any());
    assertThat(requestsCaptor.getValue())
        .extracting(StockUnitService.CreateStockUnitRequest::length)
        .containsExactly(new BigDecimal("2.5"), new BigDecimal("1.5"));
    assertThat(requestsCaptor.getValue())
        .allSatisfy(
            request -> {
              assertThat(request.unit()).isEqualTo("KG");
              assertThat(request.lengthUnit()).isEqualTo("M");
            });
  }

  @Test
  void yarnPurchaseReceiptKeepsOptionalLengthWithoutChangingWeightQuantity() {
    UUID lineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    GoodsReceiptConfirmedEvent event =
        purchaseEvent(lineId, List.of(item("BOBBIN-001", "10.0", "11.0", "250", "CM")));
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, event.getSourceId(), lineId))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-001", productId));
    when(productFacade.findById(TENANT_ID, productId))
        .thenReturn(Optional.of(ProductDto.builder().productType(ProductType.YARN).build()));
    when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
            TENANT_ID, event.getReceiptId(), BatchSourceType.PURCHASE))
        .thenReturn(Optional.empty());
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    listener.onGoodsReceiptConfirmed(event);

    ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
    verify(batchRepository).save(batchCaptor.capture());
    assertThat(batchCaptor.getValue().getUnit()).isEqualTo("KG");
    assertThat(batchCaptor.getValue().getQuantity()).isEqualByComparingTo("10.0");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockUnitService.CreateStockUnitRequest>> requestsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(stockUnitService).createBulk(any(), requestsCaptor.capture(), any());
    assertThat(requestsCaptor.getValue().get(0).length()).isEqualByComparingTo("2.5");
    assertThat(requestsCaptor.getValue().get(0).lengthUnit()).isEqualTo("M");
  }

  @Test
  void purchaseRetryReusesExistingReceiptBatchAndCompletesUnits() {
    UUID lineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    GoodsReceiptConfirmedEvent event = purchaseEvent(lineId, oneItem());
    Batch existingBatch = mock(Batch.class);
    when(existingBatch.getId()).thenReturn(batchId);
    when(existingBatch.getBatchCode()).thenReturn("PUR-2026-ABC12345");
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, event.getSourceId(), lineId))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-001", productId));
    when(productFacade.findById(TENANT_ID, productId))
        .thenReturn(Optional.of(ProductDto.builder().productType(ProductType.YARN).build()));
    when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
            TENANT_ID, event.getReceiptId(), BatchSourceType.PURCHASE))
        .thenReturn(Optional.of(existingBatch));

    listener.onGoodsReceiptConfirmed(event);

    verify(batchRepository, never()).save(any(Batch.class));
    verify(stockUnitService).createBulk(eq(batchId), any(), any());
  }

  @Test
  void fabricPurchaseEventWithMissingLengthHasDataTerminalMeasureReason() {
    UUID lineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    GoodsReceiptConfirmedEvent event = purchaseEvent(lineId, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, event.getSourceId(), lineId))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-001", productId));
    when(productFacade.findById(TENANT_ID, productId))
        .thenReturn(Optional.of(ProductDto.builder().productType(ProductType.FABRIC).build()));

    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> {
              assertThat(exception.getReason()).isEqualTo(Reason.PO_ITEM_MEASURE_INVALID);
              assertThat(exception.getMessage())
                  .contains(
                      "fabric receipt item is missing length",
                      "guidance=fix item length data or re-seed");
            });

    verify(batchRepository, never()).save(any(Batch.class));
    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void purchaseLineInfrastructureFailureRemainsRetryableProcessingFailure() {
    UUID lineId = UUID.randomUUID();
    GoodsReceiptConfirmedEvent event = purchaseEvent(lineId, oneItem());
    IllegalStateException infrastructureFailure =
        new IllegalStateException("Purchase-order gateway unavailable");
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, event.getSourceId(), lineId))
        .thenThrow(infrastructureFailure);

    assertThatThrownBy(() -> listener.onGoodsReceiptConfirmed(event))
        .isInstanceOfSatisfying(
            StockUnitMaterializationException.class,
            exception -> {
              assertThat(exception.getReason()).isEqualTo(Reason.PROCESSING_FAILED);
              assertThat(exception.getCause()).isSameAs(infrastructureFailure);
            });

    verify(batchRepository, never()).save(any(Batch.class));
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

  private GoodsReceiptConfirmedEvent purchaseEvent(
      UUID sourceLineId, List<GoodsReceiptConfirmedEvent.ReceiptItemData> items) {
    return GoodsReceiptConfirmedEvent.builder()
        .tenantId(TENANT_ID)
        .receiptId(UUID.randomUUID())
        .receiptNumber("GR-2026-ABC12345")
        .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
        .sourceId(UUID.randomUUID())
        .sourceLineId(sourceLineId)
        .supplierBatchCode("SUP-LOT-42")
        .items(items)
        .build();
  }

  private List<GoodsReceiptConfirmedEvent.ReceiptItemData> oneItem() {
    return List.of(item("UNIT-001", "10.0", "11.0"));
  }

  private GoodsReceiptConfirmedEvent.ReceiptItemData item(
      String barcode, String netWeight, String grossWeight) {
    return new GoodsReceiptConfirmedEvent.ReceiptItemData(
        UUID.randomUUID(),
        barcode,
        new BigDecimal(netWeight),
        new BigDecimal(grossWeight),
        null,
        null);
  }

  private GoodsReceiptConfirmedEvent.ReceiptItemData item(
      String barcode, String netWeight, String grossWeight, String length, String lengthUnit) {
    return new GoodsReceiptConfirmedEvent.ReceiptItemData(
        UUID.randomUUID(),
        barcode,
        new BigDecimal(netWeight),
        new BigDecimal(grossWeight),
        new BigDecimal(length),
        lengthUnit);
  }
}
