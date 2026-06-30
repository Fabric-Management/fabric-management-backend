package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.FiberPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.fabricmanagement.procurement.quote.app.SupplierQuoteService;
import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteModuleType;
import com.fabricmanagement.procurement.quote.domain.specs.FiberQuoteSpecs;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.domain.specs.FiberRFQSpecs;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.production.execution.goodsreceipt.app.GoodsReceiptService;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Seeds a coherent procurement demo dataset for master and playground tenants: suppliers,
 * PURCHASE/SUBCONTRACT work orders, an RFQ -> quote -> PO -> receipt chain, and an in-progress
 * subcontract order. It is best-effort and must never break playground initialization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProcurementDemoSeeder {

  static final String SUPPLIER_ANATOLIA = "Anatolia Cotton Co.";
  static final String SUPPLIER_AEGEAN = "Aegean Yarn Mills";
  static final String SUBCONTRACTOR_MARMARA = "Marmara Dye & Finish";

  private final TradingPartnerService tradingPartnerService;
  private final ProductFacade productFacade;
  private final WorkOrderService workOrderService;
  private final SupplierRFQService supplierRFQService;
  private final SupplierQuoteService supplierQuoteService;
  private final PurchaseOrderService purchaseOrderService;
  private final SubcontractOrderService subcontractOrderService;
  private final GoodsReceiptService goodsReceiptService;
  private final UserRepository userRepository;
  private final Clock clock;

  public void seedFor(UUID tenantId) {
    TenantContext.TenantSnapshot previous = TenantContext.capture();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(SystemUser.ID);

      if (!tradingPartnerService.searchByName(tenantId, SUPPLIER_ANATOLIA).isEmpty()) {
        log.info("Procurement demo data already exists for tenant: {}. Skipping.", tenantId);
        return;
      }

      List<ProductDto> fibers =
          productFacade.findByType(TenantContext.TEMPLATE_TENANT_ID, ProductType.FIBER);
      if (fibers.isEmpty()) {
        log.warn("No template fibers found; skipping procurement demo for tenant: {}", tenantId);
        return;
      }

      ProductDto cotton = fibers.get(0);
      ProductDto blend = fibers.size() > 1 ? fibers.get(1) : cotton;
      LocalDate today = LocalDate.now(clock);
      UUID procurementPersonaUserId = resolveProcurementPersonaUserId(tenantId);

      TradingPartnerDto anatolia =
          createPartner(
              tenantId,
              SUPPLIER_ANATOLIA,
              "TR-P2P-ANA-" + tenantSuffix(tenantId),
              "TUR",
              PartnerType.SUPPLIER,
              "NET30",
              "BCI cotton supplier");
      TradingPartnerDto aegean =
          createPartner(
              tenantId,
              SUPPLIER_AEGEAN,
              "TR-P2P-AEG-" + tenantSuffix(tenantId),
              "TUR",
              PartnerType.SUPPLIER,
              "NET45",
              "Combed yarn and fiber supplier");
      TradingPartnerDto marmara =
          createPartner(
              tenantId,
              SUBCONTRACTOR_MARMARA,
              "TR-P2P-MDF-" + tenantSuffix(tenantId),
              "TUR",
              PartnerType.SUBCONTRACTOR,
              "NET30",
              "Dyeing and finishing subcontractor");

      WorkOrderResponse chainPurchaseWo =
          approvedWorkOrder(
              cotton,
              anatolia.getId(),
              FulfillmentType.PURCHASE,
              WorkOrderModuleType.SPINNING,
              "2400.000",
              "3.850",
              "USD",
              today.plusDays(18),
              "Demo P2P chain: Aegean cotton procurement");
      approvedWorkOrder(
          blend,
          aegean.getId(),
          FulfillmentType.PURCHASE,
          WorkOrderModuleType.SPINNING,
          "1600.000",
          "4.100",
          "USD",
          today.plusDays(24),
          "Open demo flow: combed yarn input procurement");
      approvedWorkOrder(
          cotton,
          anatolia.getId(),
          FulfillmentType.PURCHASE,
          WorkOrderModuleType.GENERIC,
          "900.000",
          "3.650",
          "USD",
          today.plusDays(30),
          "Open demo flow: replenishment purchase work order");

      WorkOrderResponse chainSubcontractWo =
          approvedWorkOrder(
              blend,
              marmara.getId(),
              FulfillmentType.SUBCONTRACT,
              WorkOrderModuleType.DYEING,
              "1200.000",
              "1.350",
              "USD",
              today.plusDays(21),
              "Demo subcontract: reactive dyeing and finishing");
      approvedWorkOrder(
          cotton,
          marmara.getId(),
          FulfillmentType.SUBCONTRACT,
          WorkOrderModuleType.FINISHING,
          "700.000",
          "0.950",
          "USD",
          today.plusDays(28),
          "Open demo flow: finishing subcontract work order");

      SupplierRFQResponse sentRfq =
          createSentPurchaseRfq(
              chainPurchaseWo.id(), cotton, blend, anatolia.getId(), aegean.getId());
      createDraftPurchaseRfq(chainPurchaseWo.id(), cotton);

      List<SupplierRFQResponse.RfqLineResponse> rfqLines = sentRfq.getLines();
      createQuote(
          sentRfq.getId(),
          aegean.getId(),
          rfqLines,
          "3.92",
          "4.18",
          "14",
          "Aegean quote: balanced price and lead time");
      SupplierQuoteResponse anatoliaQuote =
          createQuote(
              sentRfq.getId(),
              anatolia.getId(),
              rfqLines,
              "3.78",
              "4.05",
              "12",
              "Anatolia quote: preferred quality lot");

      supplierQuoteService.startReview(anatoliaQuote.getId());
      SupplierQuoteResponse acceptedQuote =
          supplierQuoteService.markAsAccepted(anatoliaQuote.getId());

      PurchaseOrderResponse po =
          createConfirmedPurchaseOrder(
              chainPurchaseWo.id(),
              anatolia.getId(),
              acceptedQuote.getId(),
              rfqLines,
              procurementPersonaUserId);
      createConfirmedGoodsReceipt(po.getId());

      createInProgressSubcontract(chainSubcontractWo.id(), marmara.getId(), cotton, blend, today);

      log.info("Successfully provisioned procurement demo data for tenant: {}", tenantId);
    } catch (Exception e) {
      log.warn("Procurement demo seeding failed for tenant {} - continuing.", tenantId, e);
    } finally {
      TenantContext.restore(previous);
    }
  }

  private TradingPartnerDto createPartner(
      UUID tenantId,
      String name,
      String taxId,
      String country,
      PartnerType type,
      String paymentTerms,
      String notes) {
    CreateTradingPartnerRequest req = new CreateTradingPartnerRequest();
    req.setCompanyName(name);
    req.setCustomName(name);
    req.setTaxId(taxId);
    req.setCountry(country);
    req.setPartnerType(type);
    req.setRelationshipMeta(
        Map.of("payment_terms", paymentTerms, "contact_email", contactEmail(name), "notes", notes));
    return tradingPartnerService.createPartner(req);
  }

  private WorkOrderResponse approvedWorkOrder(
      ProductDto product,
      UUID partnerId,
      FulfillmentType fulfillmentType,
      WorkOrderModuleType moduleType,
      String qty,
      String unitCost,
      String currency,
      LocalDate deadline,
      String notes) {
    WorkOrderResponse created =
        workOrderService.createWorkOrder(
            CreateWorkOrderRequest.builder()
                .outputProductId(product.getId())
                .moduleType(moduleType)
                .tradingPartnerId(partnerId)
                .fulfillmentType(fulfillmentType)
                .plannedQty(new BigDecimal(qty))
                .unit(unit(product))
                .unitCost(new BigDecimal(unitCost))
                .currency(currency)
                .deadline(deadline)
                .certificationReq("BCI")
                .originReq("TR")
                .notes(notes)
                .build());
    workOrderService.changeStatus(created.id(), WorkOrderStatus.PENDING_APPROVAL);
    return workOrderService.changeStatus(created.id(), WorkOrderStatus.APPROVED);
  }

  private SupplierRFQResponse createSentPurchaseRfq(
      UUID workOrderId, ProductDto cotton, ProductDto blend, UUID supplierOne, UUID supplierTwo) {
    SupplierRFQResponse rfq =
        createRfq(
            workOrderId, SupplierRFQType.PURCHASE, "Demo RFQ: cotton lots for May production");
    rfq = addFiberRfqLine(rfq.getId(), cotton, "1400.000", "Aegean cotton, BCI, 28.5 mm staple");
    rfq = addFiberRfqLine(rfq.getId(), blend, "1000.000", "Combed cotton blend, low trash");
    addRecipient(rfq.getId(), supplierOne);
    addRecipient(rfq.getId(), supplierTwo);
    return supplierRFQService.sendRfq(rfq.getId());
  }

  private void createDraftPurchaseRfq(UUID workOrderId, ProductDto product) {
    SupplierRFQResponse rfq =
        createRfq(workOrderId, SupplierRFQType.PURCHASE, "Draft RFQ: replenishment fiber options");
    addFiberRfqLine(
        rfq.getId(), product, "650.000", "Draft line for procurement persona create flow");
  }

  private SupplierRFQResponse createRfq(UUID workOrderId, SupplierRFQType type, String notes) {
    CreateSupplierRFQRequest req = new CreateSupplierRFQRequest();
    req.setWorkOrderId(workOrderId);
    req.setModuleType(SupplierRFQModuleType.FIBER);
    req.setRfqType(type);
    req.setDeadline(Instant.now(clock).plusSeconds(10L * 24 * 60 * 60));
    req.setNotes(notes);
    return supplierRFQService.createRfq(req);
  }

  private SupplierRFQResponse addFiberRfqLine(
      UUID rfqId, ProductDto product, String qty, String description) {
    return supplierRFQService.addLine(
        rfqId,
        new AddRfqLineRequest(
            product.getId(),
            description,
            new BigDecimal(qty),
            unit(product),
            new FiberRFQSpecs(
                new BigDecimal("4.20"),
                new BigDecimal("28.50"),
                new BigDecimal("30.50"),
                new BigDecimal("1.20"),
                "BCI certified, contamination controlled")));
  }

  private void addRecipient(UUID rfqId, UUID tradingPartnerId) {
    AddRecipientRequest req = new AddRecipientRequest();
    req.setTradingPartnerId(tradingPartnerId);
    req.setResponseDeadline(Instant.now(clock).plusSeconds(7L * 24 * 60 * 60));
    supplierRFQService.addRecipient(rfqId, req);
  }

  private SupplierQuoteResponse createQuote(
      UUID rfqId,
      UUID supplierId,
      List<SupplierRFQResponse.RfqLineResponse> rfqLines,
      String firstUnitPrice,
      String secondUnitPrice,
      String leadDays,
      String notes) {
    SupplierQuoteResponse quote =
        supplierQuoteService.createQuote(
            new CreateSupplierQuoteRequest(
                rfqId,
                supplierId,
                LocalDate.now(clock).plusDays(14),
                "USD",
                "NET30",
                Integer.valueOf(leadDays),
                QuoteEntryMethod.MANUAL_ENTRY,
                SupplierQuoteModuleType.FIBER,
                notes));

    supplierQuoteService.addLine(
        quote.getId(), quoteLine(rfqLines.get(0), firstUnitPrice, "Class A cotton lot"));
    supplierQuoteService.addLine(
        quote.getId(), quoteLine(rfqLines.get(1), secondUnitPrice, "Low trash blend lot"));
    return quote;
  }

  private AddQuoteLineRequest quoteLine(
      SupplierRFQResponse.RfqLineResponse rfqLine, String unitPrice, String notes) {
    return new AddQuoteLineRequest(
        rfqLine.getId(),
        new BigDecimal(unitPrice),
        "USD",
        rfqLine.getRequestedQty(),
        rfqLine.getUnit(),
        Map.of("break_kg", "1000", "discount_pct", "1.5"),
        new FiberQuoteSpecs(
            new BigDecimal("4.20"),
            new BigDecimal("28.70"),
            new BigDecimal("31.20"),
            new BigDecimal("1.00"),
            "HVI report available"),
        notes);
  }

  private PurchaseOrderResponse createConfirmedPurchaseOrder(
      UUID workOrderId,
      UUID supplierId,
      UUID quoteId,
      List<SupplierRFQResponse.RfqLineResponse> rfqLines,
      UUID ownerUserId) {
    PurchaseOrderResponse po =
        executeAsUser(
            ownerUserId,
            () ->
                purchaseOrderService.createPurchaseOrder(
                    CreatePurchaseOrderRequest.builder()
                        .workOrderId(workOrderId)
                        .tradingPartnerId(supplierId)
                        .supplierQuoteId(quoteId)
                        .currency("USD")
                        .paymentTerms("NET30")
                        .expectedDelivery(LocalDate.now(clock).plusDays(16))
                        .notes("Demo PO generated from accepted supplier quote")
                        .moduleType(PurchaseOrderModuleType.FIBER)
                        .moduleSpecs(fiberPurchaseSpecs())
                        .lines(
                            rfqLines.stream()
                                .map(
                                    line ->
                                        CreatePurchaseOrderRequest.PurchaseOrderLineRequest
                                            .builder()
                                            .rfqLineId(line.getId())
                                            .productId(line.getProductId())
                                            .productDesc(line.getProductDesc())
                                            .qty(line.getRequestedQty())
                                            .unit(line.getUnit())
                                            .unitPrice(new BigDecimal("3.78"))
                                            .currency("USD")
                                            .moduleSpecs(fiberPurchaseSpecs())
                                            .build())
                                .toList())
                        .build()));

    po = purchaseOrderService.changeStatus(po.getId(), PurchaseOrderStatus.SENT);
    if (po.getStatus() == PurchaseOrderStatus.PENDING_APPROVAL) {
      po = purchaseOrderService.changeStatusAsSystem(po.getId(), PurchaseOrderStatus.SENT);
    }
    return purchaseOrderService.changeStatus(po.getId(), PurchaseOrderStatus.CONFIRMED);
  }

  private UUID resolveProcurementPersonaUserId(UUID tenantId) {
    return userRepository
        .findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(tenantId, "Yolanda", "Bidwell")
        .map(com.fabricmanagement.platform.user.domain.User::getId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Procurement demo persona Yolanda Bidwell is required before seeding POs"));
  }

  private <T> T executeAsUser(UUID userId, Supplier<T> supplier) {
    TenantContext.TenantSnapshot previous = TenantContext.capture();
    try {
      TenantContext.setCurrentUserId(userId);
      return supplier.get();
    } finally {
      TenantContext.restore(previous);
    }
  }

  private void createConfirmedGoodsReceipt(UUID poId) {
    var receipt =
        goodsReceiptService.createGoodsReceipt(
            CreateGoodsReceiptRequest.builder()
                .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
                .sourceId(poId)
                .receivedById(SystemUser.ID)
                .receivedAt(Instant.now(clock).minusSeconds(2L * 24 * 60 * 60))
                .packageCount(3)
                .vehicleInfo("34 P2P 026 - sealed truck")
                .items(
                    List.of(
                        receiptItem("P2P-BALE-001", "812.500", "825.000"),
                        receiptItem("P2P-BALE-002", "790.000", "802.000"),
                        receiptItem("P2P-BALE-003", "797.500", "810.000")))
                .build());
    goodsReceiptService.confirmGoodsReceipt(receipt.getId());
  }

  private CreateGoodsReceiptRequest.GoodsReceiptItemRequest receiptItem(
      String serialNumber, String netWeight, String grossWeight) {
    return CreateGoodsReceiptRequest.GoodsReceiptItemRequest.builder()
        .serialNumber(serialNumber)
        .netWeight(new BigDecimal(netWeight))
        .grossWeight(new BigDecimal(grossWeight))
        .notes("Demo accepted bale")
        .build();
  }

  private SubcontractOrderResponse createInProgressSubcontract(
      UUID workOrderId,
      UUID subcontractorId,
      ProductDto inputProduct,
      ProductDto outputProduct,
      LocalDate today) {
    SubcontractOrderResponse sc =
        subcontractOrderService.createSubcontractOrder(
            CreateSubcontractOrderRequest.builder()
                .workOrderId(workOrderId)
                .tradingPartnerId(subcontractorId)
                .inputProductId(inputProduct.getId())
                .outputProductId(outputProduct.getId())
                .productSentQty(new BigDecimal("1180.000"))
                .expectedOutputQty(new BigDecimal("1145.000"))
                .agreedUnitPrice(new BigDecimal("1.35"))
                .currency("USD")
                .expectedReturnDate(today.plusDays(12))
                .notes("Demo subcontract: dyeing in progress at Marmara")
                .build());
    sc = subcontractOrderService.changeStatus(sc.getId(), SubcontractOrderStatus.CONFIRMED, null);
    sc =
        subcontractOrderService.changeStatus(sc.getId(), SubcontractOrderStatus.PRODUCT_SENT, null);
    return subcontractOrderService.changeStatus(
        sc.getId(), SubcontractOrderStatus.IN_PROGRESS, null);
  }

  private FiberPurchaseSpecs fiberPurchaseSpecs() {
    return new FiberPurchaseSpecs(
        28.5, "A", 8.2, 4.2, 30.5, 83.0, 1.1, "41-3", "Turkey-Aegean", List.of("BCI"), "2025/2026");
  }

  private String unit(ProductDto product) {
    return product.getUnit() != null && !product.getUnit().isBlank() ? product.getUnit() : "KG";
  }

  private String contactEmail(String name) {
    return name.toLowerCase()
            .replace("&", "and")
            .replaceAll("[^a-z0-9]+", ".")
            .replaceAll("\\.+$", "")
        + "@demo.fabric";
  }

  private String tenantSuffix(UUID tenantId) {
    return tenantId.toString().substring(0, 8).toUpperCase();
  }
}
