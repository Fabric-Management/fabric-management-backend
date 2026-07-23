package com.fabricmanagement.production.execution.goodsreceipt.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.procurement.purchaseorder.api.query.PurchaseOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.production.execution.batch.api.query.BatchQueryService;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceipt;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptItem;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.domain.exception.GoodsReceiptDomainException;
import com.fabricmanagement.production.execution.goodsreceipt.domain.port.PoReceivabilityPort;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.goodsreceipt.dto.GoodsReceiptResponse;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptItemRepository;
import com.fabricmanagement.production.execution.goodsreceipt.infra.repository.GoodsReceiptRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PO_ID = UUID.randomUUID();
  private static final UUID LINE_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();

  @Mock private GoodsReceiptRepository receiptRepository;
  @Mock private GoodsReceiptItemRepository itemRepository;
  @Mock private BatchQueryService batchQueryService;
  @Mock private PurchaseOrderQueryService purchaseOrderQueryService;
  @Mock private SubcontractOrderQueryService subcontractOrderQueryService;
  @Mock private ProductFacade productFacade;
  @Mock private PoReceivabilityPort poReceivabilityPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private GoodsReceiptService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    org.mockito.Mockito.lenient()
        .when(poReceivabilityPort.isReceivable(TENANT_ID, PO_ID))
        .thenReturn(true);
    service =
        new GoodsReceiptService(
            receiptRepository,
            itemRepository,
            batchQueryService,
            purchaseOrderQueryService,
            subcontractOrderQueryService,
            productFacade,
            new BatchPrimaryMeasureService(),
            poReceivabilityPort,
            eventPublisher);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void purchaseReceiptWithoutSourceLineReturnsCoded422() {
    CreateGoodsReceiptRequest request = purchaseRequest(null, item(null, null));

    assertCoded422(() -> service.createGoodsReceipt(request), "GR_SOURCE_LINE_REQUIRED");
  }

  @Test
  void purchaseReceiptCreateRejectsNonReceivablePurchaseOrder() {
    when(poReceivabilityPort.isReceivable(TENANT_ID, PO_ID)).thenReturn(false);

    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))),
        "GR_PO_NOT_RECEIVABLE");
  }

  @Test
  void purchaseReceiptConfirmRechecksReceivability() {
    GoodsReceipt draft =
        GoodsReceipt.builder()
            .receiptNumber("GR-STATUS-GUARD")
            .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
            .sourceId(PO_ID)
            .sourceLineId(LINE_ID)
            .status(GoodsReceiptStatus.DRAFT)
            .build();
    when(receiptRepository.findById(any())).thenReturn(Optional.of(draft));
    when(itemRepository.findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(any()))
        .thenReturn(
            List.of(
                GoodsReceiptItem.builder()
                    .barcode("STATUS-001")
                    .netWeight(BigDecimal.TEN)
                    .build()));
    when(poReceivabilityPort.isReceivable(TENANT_ID, PO_ID)).thenReturn(false);

    assertCoded422(() -> service.confirmGoodsReceipt(UUID.randomUUID()), "GR_PO_NOT_RECEIVABLE");
  }

  @Test
  void productlessOrUnsupportedPurchaseLineReturnsNotMaterializable() {
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, PO_ID, LINE_ID))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-2026-001", null));
    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))),
        "GR_PO_LINE_NOT_MATERIALIZABLE");

    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, PO_ID, LINE_ID))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-2026-001", PRODUCT_ID));
    when(productFacade.findById(TENANT_ID, PRODUCT_ID))
        .thenReturn(Optional.of(ProductDto.builder().productType(ProductType.CHEMICAL).build()));
    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))),
        "GR_PO_LINE_NOT_MATERIALIZABLE");
  }

  @Test
  void missingPurchaseLineReturnsNotMaterializable() {
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, PO_ID, LINE_ID))
        .thenThrow(new NotFoundException("Purchase-order line not found"));

    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))),
        "GR_PO_LINE_NOT_MATERIALIZABLE");
  }

  @Test
  void purchaseLineInfrastructureFailureIsNotConvertedTo422() {
    IllegalStateException infrastructureFailure =
        new IllegalStateException("Purchase-order gateway unavailable");
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, PO_ID, LINE_ID))
        .thenThrow(infrastructureFailure);

    assertThatThrownBy(() -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))))
        .isSameAs(infrastructureFailure);
  }

  @Test
  void fabricRequiresConvertibleLengthAtCreateAndConfirm() {
    stubProduct(ProductType.FABRIC);
    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item(null, null))),
        "GR_ITEM_LENGTH_REQUIRED");
    assertCoded422(
        () -> service.createGoodsReceipt(purchaseRequest(LINE_ID, item("2", "FT"))),
        "GR_ITEM_UNIT_INVALID");

    GoodsReceipt draft =
        GoodsReceipt.builder()
            .receiptNumber("GR-LEGACY")
            .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
            .sourceId(PO_ID)
            .sourceLineId(LINE_ID)
            .status(GoodsReceiptStatus.DRAFT)
            .build();
    when(receiptRepository.findById(any())).thenReturn(Optional.of(draft));
    when(itemRepository.findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(any()))
        .thenReturn(
            List.of(
                GoodsReceiptItem.builder()
                    .barcode("LEGACY-001")
                    .netWeight(BigDecimal.TEN)
                    .build()));

    assertCoded422(() -> service.confirmGoodsReceipt(UUID.randomUUID()), "GR_ITEM_LENGTH_REQUIRED");
  }

  @Test
  void repeatDeliveriesUseDistinctReceiptScopedBarcodesWithinColumnLimit() {
    stubProduct(ProductType.YARN);
    when(receiptRepository.save(any(GoodsReceipt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(itemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(purchaseOrderQueryService.getPurchaseOrderNumber(PO_ID)).thenReturn("PO-2026-001");

    GoodsReceiptResponse first =
        service.createGoodsReceipt(purchaseRequest(LINE_ID, item("250", "CM")));
    GoodsReceiptResponse second =
        service.createGoodsReceipt(purchaseRequest(LINE_ID, item("250", "CM")));

    String firstBarcode = first.getItems().get(0).getBarcode();
    String secondBarcode = second.getItems().get(0).getBarcode();
    assertThat(firstBarcode).isNotEqualTo(secondBarcode);
    assertThat(firstBarcode).startsWith("PO-2026-001-GR-").endsWith("-001");
    assertThat(firstBarcode.length()).isLessThanOrEqualTo(50);
  }

  private void stubProduct(ProductType productType) {
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, PO_ID, LINE_ID))
        .thenReturn(new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-2026-001", PRODUCT_ID));
    when(productFacade.findById(TENANT_ID, PRODUCT_ID))
        .thenReturn(Optional.of(ProductDto.builder().productType(productType).build()));
  }

  private CreateGoodsReceiptRequest purchaseRequest(
      UUID sourceLineId, CreateGoodsReceiptRequest.GoodsReceiptItemRequest item) {
    return CreateGoodsReceiptRequest.builder()
        .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
        .sourceId(PO_ID)
        .sourceLineId(sourceLineId)
        .receivedById(UUID.randomUUID())
        .packageCount(1)
        .items(List.of(item))
        .build();
  }

  private CreateGoodsReceiptRequest.GoodsReceiptItemRequest item(String length, String lengthUnit) {
    return CreateGoodsReceiptRequest.GoodsReceiptItemRequest.builder()
        .netWeight(BigDecimal.TEN)
        .length(length != null ? new BigDecimal(length) : null)
        .lengthUnit(lengthUnit)
        .build();
  }

  private void assertCoded422(Runnable operation, String errorCode) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            GoodsReceiptDomainException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(errorCode);
              assertThat(exception.getHttpStatus()).isEqualTo(422);
            });
  }
}
