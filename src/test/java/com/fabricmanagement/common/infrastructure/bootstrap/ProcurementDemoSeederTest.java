package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderConstraintViolationMatcher;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.fabricmanagement.procurement.quote.app.SupplierQuoteService;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.production.execution.goodsreceipt.app.GoodsReceiptService;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.goodsreceipt.dto.GoodsReceiptResponse;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProcurementDemoSeederTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private TradingPartnerService tradingPartnerService;
  @Mock private ProductFacade productFacade;
  @Mock private WorkOrderService workOrderService;
  @Mock private SupplierRFQService supplierRFQService;
  @Mock private SupplierQuoteService supplierQuoteService;
  @Mock private PurchaseOrderService purchaseOrderService;
  @Mock private SubcontractOrderService subcontractOrderService;
  @Mock private GoodsReceiptService goodsReceiptService;
  @Mock private UserRepository userRepository;

  private final List<TradingPartnerDto> partners = new ArrayList<>();
  private final Map<UUID, SupplierRFQ> rfqs = new HashMap<>();

  private ProcurementDemoSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-06-21T10:00:00Z"), ZoneId.of("UTC"));
    seeder =
        new ProcurementDemoSeeder(
            tradingPartnerService,
            productFacade,
            workOrderService,
            supplierRFQService,
            supplierQuoteService,
            purchaseOrderService,
            subcontractOrderService,
            goodsReceiptService,
            userRepository,
            clock);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void seedFor_createsProcurementDatasetAndIsIdempotent() {
    ProductDto cotton = product("Aegean Cotton Fiber");
    ProductDto blend = product("Combed Cotton Blend");
    when(productFacade.findByType(eq(TenantContext.TEMPLATE_TENANT_ID), eq(ProductType.FIBER)))
        .thenReturn(List.of(cotton, blend));
    when(userRepository.findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(
            TENANT_ID, "Yolanda", "Bidwell"))
        .thenReturn(Optional.of(user(UUID.randomUUID())));

    when(tradingPartnerService.searchByName(TENANT_ID, ProcurementDemoSeeder.SUPPLIER_ANATOLIA))
        .thenAnswer(
            invocation ->
                partners.stream()
                    .filter(p -> ProcurementDemoSeeder.SUPPLIER_ANATOLIA.equals(p.getDisplayName()))
                    .toList());
    when(tradingPartnerService.createPartner(any(CreateTradingPartnerRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateTradingPartnerRequest req = invocation.getArgument(0);
              TradingPartnerDto dto =
                  TradingPartnerDto.builder()
                      .id(UUID.randomUUID())
                      .displayName(req.getCompanyName())
                      .partnerType(req.getPartnerType())
                      .build();
              partners.add(dto);
              return dto;
            });

    when(workOrderService.createWorkOrder(any(CreateWorkOrderRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateWorkOrderRequest req = invocation.getArgument(0);
              return WorkOrderResponse.builder()
                  .id(UUID.randomUUID())
                  .outputProductId(req.outputProductId())
                  .fulfillmentType(req.fulfillmentType())
                  .status(WorkOrderStatus.DRAFT)
                  .build();
            });
    when(workOrderService.changeStatus(any(), any()))
        .thenAnswer(
            invocation ->
                WorkOrderResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(invocation.getArgument(1))
                    .build());

    when(supplierRFQService.createRfq(any(CreateSupplierRFQRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateSupplierRFQRequest req = invocation.getArgument(0);
              SupplierRFQ rfq = new SupplierRFQ();
              rfq.setId(UUID.randomUUID());
              rfq.setTenantId(TENANT_ID);
              rfq.setWorkOrderId(req.getWorkOrderId());
              rfq.setModuleType(req.getModuleType());
              rfq.setRfqType(req.getRfqType());
              rfq.setStatus(SupplierRFQStatus.DRAFT);
              rfqs.put(rfq.getId(), rfq);
              return SupplierRFQResponse.from(rfq);
            });
    when(supplierRFQService.addLine(any(), any(AddRfqLineRequest.class)))
        .thenAnswer(
            invocation -> {
              UUID rfqId = invocation.getArgument(0);
              AddRfqLineRequest req = invocation.getArgument(1);
              SupplierRFQLine line = new SupplierRFQLine();
              line.setId(UUID.randomUUID());
              line.setTenantId(TENANT_ID);
              line.setProductId(req.productId());
              line.setProductDesc(req.productDesc());
              line.setRequestedQty(req.requestedQty());
              line.setUnit(req.unit());
              line.setModuleSpecs(req.moduleSpecs());
              rfqs.get(rfqId).addLine(line);
              return SupplierRFQResponse.from(rfqs.get(rfqId));
            });
    when(supplierRFQService.addRecipient(any(), any(AddRecipientRequest.class)))
        .thenAnswer(invocation -> SupplierRFQResponse.from(rfqs.get(invocation.getArgument(0))));
    when(supplierRFQService.sendRfq(any()))
        .thenAnswer(
            invocation -> {
              SupplierRFQ rfq = rfqs.get(invocation.getArgument(0));
              rfq.setStatus(SupplierRFQStatus.SENT);
              return SupplierRFQResponse.from(rfq);
            });

    when(supplierQuoteService.createQuote(any(CreateSupplierQuoteRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateSupplierQuoteRequest req = invocation.getArgument(0);
              return SupplierQuoteResponse.builder()
                  .id(UUID.randomUUID())
                  .rfqId(req.rfqId())
                  .tradingPartnerId(req.tradingPartnerId())
                  .status(SupplierQuoteStatus.RECEIVED)
                  .build();
            });
    when(supplierQuoteService.addLine(any(), any(AddQuoteLineRequest.class)))
        .thenAnswer(
            invocation ->
                SupplierQuoteResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(SupplierQuoteStatus.RECEIVED)
                    .build());
    when(supplierQuoteService.startReview(any()))
        .thenAnswer(
            invocation ->
                SupplierQuoteResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(SupplierQuoteStatus.UNDER_REVIEW)
                    .build());
    when(supplierQuoteService.markAsAccepted(any()))
        .thenAnswer(
            invocation ->
                SupplierQuoteResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(SupplierQuoteStatus.ACCEPTED)
                    .build());

    when(purchaseOrderService.createPurchaseOrder(any(CreatePurchaseOrderRequest.class)))
        .thenAnswer(
            invocation ->
                PurchaseOrderResponse.builder()
                    .id(UUID.randomUUID())
                    .status(PurchaseOrderStatus.DRAFT)
                    .build());
    when(purchaseOrderService.changeStatus(any(), any()))
        .thenAnswer(
            invocation ->
                PurchaseOrderResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(invocation.getArgument(1))
                    .build());

    when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
        .thenAnswer(
            invocation ->
                GoodsReceiptResponse.builder()
                    .id(UUID.randomUUID())
                    .status(GoodsReceiptStatus.DRAFT)
                    .build());
    when(goodsReceiptService.confirmGoodsReceipt(any()))
        .thenAnswer(
            invocation ->
                GoodsReceiptResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(GoodsReceiptStatus.CONFIRMED)
                    .build());

    when(subcontractOrderService.createSubcontractOrder(any(CreateSubcontractOrderRequest.class)))
        .thenAnswer(
            invocation ->
                SubcontractOrderResponse.builder()
                    .id(UUID.randomUUID())
                    .status(SubcontractOrderStatus.DRAFT)
                    .build());
    when(subcontractOrderService.changeStatus(any(), any(), any()))
        .thenAnswer(
            invocation ->
                SubcontractOrderResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(invocation.getArgument(1))
                    .build());

    seeder.seedFor(TENANT_ID);
    seeder.seedFor(TENANT_ID);

    ArgumentCaptor<CreateTradingPartnerRequest> partnerCaptor =
        ArgumentCaptor.forClass(CreateTradingPartnerRequest.class);
    verify(tradingPartnerService, times(3)).createPartner(partnerCaptor.capture());
    assertThat(partnerCaptor.getAllValues())
        .filteredOn(req -> req.getPartnerType() == PartnerType.SUPPLIER)
        .hasSizeGreaterThanOrEqualTo(1);
    assertThat(partnerCaptor.getAllValues())
        .filteredOn(req -> req.getPartnerType() == PartnerType.SUBCONTRACTOR)
        .hasSizeGreaterThanOrEqualTo(1);

    ArgumentCaptor<CreateWorkOrderRequest> workOrderCaptor =
        ArgumentCaptor.forClass(CreateWorkOrderRequest.class);
    verify(workOrderService, times(5)).createWorkOrder(workOrderCaptor.capture());
    assertThat(workOrderCaptor.getAllValues())
        .filteredOn(req -> req.fulfillmentType() == FulfillmentType.PURCHASE)
        .hasSizeGreaterThanOrEqualTo(1);
    assertThat(workOrderCaptor.getAllValues())
        .filteredOn(req -> req.fulfillmentType() == FulfillmentType.SUBCONTRACT)
        .hasSizeGreaterThanOrEqualTo(1);

    verify(supplierRFQService, times(2)).createRfq(any(CreateSupplierRFQRequest.class));
    verify(supplierQuoteService, times(2)).createQuote(any(CreateSupplierQuoteRequest.class));
    verify(purchaseOrderService, times(1))
        .createPurchaseOrder(any(CreatePurchaseOrderRequest.class));
    verify(goodsReceiptService, times(1)).createGoodsReceipt(any(CreateGoodsReceiptRequest.class));
    verify(subcontractOrderService, times(1))
        .createSubcontractOrder(any(CreateSubcontractOrderRequest.class));
  }

  @Test
  void seedFor_neverThrowsWhenDependencyFails() {
    when(tradingPartnerService.searchByName(TENANT_ID, ProcurementDemoSeeder.SUPPLIER_ANATOLIA))
        .thenReturn(List.of());
    when(productFacade.findByType(eq(TenantContext.TEMPLATE_TENANT_ID), eq(ProductType.FIBER)))
        .thenThrow(new IllegalStateException("template unavailable"));

    assertThatCode(() -> seeder.seedFor(TENANT_ID)).doesNotThrowAnyException();
  }

  @Test
  void createConfirmedPurchaseOrder_whenSeederLosesRace_continuesWithWinningOrder() {
    UUID quoteId = UUID.randomUUID();
    UUID winningOrderId = UUID.randomUUID();
    UUID ownerUserId = UUID.randomUUID();
    SupplierRFQResponse.RfqLineResponse rfqLine =
        SupplierRFQResponse.RfqLineResponse.builder()
            .id(UUID.randomUUID())
            .requestedQty(java.math.BigDecimal.TEN)
            .unit("KG")
            .build();

    TenantContext.setCurrentTenantId(TENANT_ID);
    doThrow(
            new DataIntegrityViolationException(
                "duplicate "
                    + PurchaseOrderConstraintViolationMatcher.ACTIVE_SUPPLIER_QUOTE_CONSTRAINT))
        .when(purchaseOrderService)
        .createPurchaseOrder(any(CreatePurchaseOrderRequest.class));
    when(purchaseOrderService.getActivePurchaseOrderBySupplierQuote(TENANT_ID, quoteId))
        .thenReturn(
            PurchaseOrderResponse.builder()
                .id(winningOrderId)
                .status(PurchaseOrderStatus.DRAFT)
                .build());
    when(purchaseOrderService.changeStatus(any(), any()))
        .thenAnswer(
            invocation ->
                PurchaseOrderResponse.builder()
                    .id(invocation.getArgument(0))
                    .status(invocation.getArgument(1))
                    .build());
    UUID receiptId = UUID.randomUUID();
    when(goodsReceiptService.createGoodsReceipt(any(CreateGoodsReceiptRequest.class)))
        .thenReturn(
            GoodsReceiptResponse.builder().id(receiptId).status(GoodsReceiptStatus.DRAFT).build());
    when(goodsReceiptService.confirmGoodsReceipt(receiptId))
        .thenReturn(
            GoodsReceiptResponse.builder()
                .id(receiptId)
                .status(GoodsReceiptStatus.CONFIRMED)
                .build());

    PurchaseOrderResponse result =
        seeder.createConfirmedPurchaseOrderAndReceipt(
            UUID.randomUUID(), UUID.randomUUID(), quoteId, List.of(rfqLine), ownerUserId);

    assertThat(result.getId()).isEqualTo(winningOrderId);
    assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
    verify(purchaseOrderService).getActivePurchaseOrderBySupplierQuote(TENANT_ID, quoteId);
    verify(purchaseOrderService).changeStatus(winningOrderId, PurchaseOrderStatus.CONFIRMED);
    ArgumentCaptor<CreateGoodsReceiptRequest> receiptCaptor =
        ArgumentCaptor.forClass(CreateGoodsReceiptRequest.class);
    verify(goodsReceiptService).createGoodsReceipt(receiptCaptor.capture());
    assertThat(receiptCaptor.getValue().getSourceId()).isEqualTo(winningOrderId);
    verify(goodsReceiptService).confirmGoodsReceipt(receiptId);
  }

  private ProductDto product(String name) {
    return ProductDto.builder()
        .id(UUID.randomUUID())
        .displayName(name)
        .productType(ProductType.FIBER)
        .unit("KG")
        .build();
  }

  private User user(UUID id) {
    User user = User.builder().firstName("Yolanda").lastName("Bidwell").build();
    user.setId(id);
    return user;
  }
}
