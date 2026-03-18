package com.fabricmanagement.procurement.quote.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orchestrates the automatic creation of PurchaseOrder or SubcontractOrder when a SupplierQuote is
 * accepted. Determines order type based on the parent RFQ's rfqType (PURCHASE vs SUBCONTRACT).
 *
 * <p>Idempotency: Checks for existing PO/SO before creation to prevent duplicates from event
 * replays or retries.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QuoteToOrderOrchestrator {

  private final SupplierQuoteRepository quoteRepository;
  private final SupplierRFQRepository rfqRepository;
  private final PurchaseOrderService purchaseOrderService;
  private final PurchaseOrderRepository purchaseOrderRepository;
  private final SubcontractOrderService subcontractOrderService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onQuoteAccepted(SupplierQuoteAcceptedEvent event) {
    log.info("QuoteToOrderOrchestrator ← QuoteAccepted: quote={}", event.getQuoteId());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          quoteRepository
              .findByTenantIdAndIdAndIsActiveTrue(event.getTenantId(), event.getQuoteId())
              .ifPresentOrElse(
                  quote ->
                      rfqRepository
                          .findByTenantIdAndIdAndIsActiveTrue(event.getTenantId(), quote.getRfqId())
                          .ifPresentOrElse(
                              rfq -> routeToOrder(quote, rfq),
                              () ->
                                  log.warn(
                                      "RFQ not found for quote {} (rfqId={}), skipping order creation",
                                      event.getQuoteId(),
                                      quote.getRfqId())),
                  () ->
                      log.warn("Quote not found: {}, skipping order creation", event.getQuoteId()));
        });
  }

  private void routeToOrder(SupplierQuote quote, SupplierRFQ rfq) {
    if (rfq.getRfqType() == SupplierRFQType.PURCHASE) {
      createPurchaseOrder(quote, rfq);
    } else if (rfq.getRfqType() == SupplierRFQType.SUBCONTRACT) {
      createSubcontractOrder(quote, rfq);
    } else {
      log.warn(
          "Unknown RFQ type '{}' for quote {}, skipping order creation",
          rfq.getRfqType(),
          quote.getQuoteNumber());
    }
  }

  private void createPurchaseOrder(SupplierQuote quote, SupplierRFQ rfq) {
    // Idempotency check: prevent duplicate PO for the same quote
    if (purchaseOrderRepository.existsBySupplierQuoteIdAndIsActiveTrue(quote.getId())) {
      log.warn(
          "PurchaseOrder already exists for quote {}, skipping duplicate creation",
          quote.getQuoteNumber());
      return;
    }

    List<CreatePurchaseOrderRequest.PurchaseOrderLineRequest> lines =
        quote.getLines().stream()
            .map(
                line -> {
                  var matchingRfqLine =
                      rfq.getLines().stream()
                          .filter(rl -> rl.getId().equals(line.getRfqLineId()))
                          .findFirst();

                  return CreatePurchaseOrderRequest.PurchaseOrderLineRequest.builder()
                      .rfqLineId(line.getRfqLineId())
                      .materialId(matchingRfqLine.map(rl -> rl.getMaterialId()).orElse(null))
                      .productDesc(matchingRfqLine.map(rl -> rl.getProductDesc()).orElse(null))
                      .qty(line.getQty())
                      .unit(line.getUnit())
                      .unitPrice(line.getUnitPrice())
                      .currency(line.getCurrency())
                      .build();
                })
            .toList();

    CreatePurchaseOrderRequest poRequest =
        CreatePurchaseOrderRequest.builder()
            .workOrderId(rfq.getWorkOrderId())
            .tradingPartnerId(quote.getTradingPartnerId())
            .supplierQuoteId(quote.getId())
            .currency(quote.getCurrency())
            .paymentTerms(quote.getPaymentTerms())
            .notes("Auto-generated from Quote: " + quote.getQuoteNumber())
            .lines(lines)
            .build();

    try {
      purchaseOrderService.createPurchaseOrder(poRequest);
      log.info("Successfully created PurchaseOrder for quote {}", quote.getQuoteNumber());
    } catch (Exception e) {
      log.error("Failed to create PurchaseOrder for quote {}", quote.getQuoteNumber(), e);
    }
  }

  private void createSubcontractOrder(SupplierQuote quote, SupplierRFQ rfq) {
    if (quote.getLines() == null || quote.getLines().isEmpty()) {
      log.warn(
          "Cannot create SubcontractOrder for quote {} — no lines found", quote.getQuoteNumber());
      return;
    }

    SupplierQuoteLine firstLine = quote.getLines().get(0);
    var matchingRfqLine =
        rfq.getLines().stream()
            .filter(rl -> rl.getId().equals(firstLine.getRfqLineId()))
            .findFirst();

    CreateSubcontractOrderRequest soRequest =
        CreateSubcontractOrderRequest.builder()
            .workOrderId(rfq.getWorkOrderId())
            .tradingPartnerId(quote.getTradingPartnerId())
            .materialId(matchingRfqLine.map(rl -> rl.getMaterialId()).orElse(null))
            .materialSentQty(firstLine.getQty())
            .unit(firstLine.getUnit())
            .agreedUnitPrice(firstLine.getUnitPrice())
            .currency(firstLine.getCurrency())
            .notes("Auto-generated from Subcontract Quote: " + quote.getQuoteNumber())
            .build();

    try {
      subcontractOrderService.createSubcontractOrder(soRequest);
      log.info("Successfully created SubcontractOrder for quote {}", quote.getQuoteNumber());
    } catch (Exception e) {
      log.error("Failed to create SubcontractOrder for quote {}", quote.getQuoteNumber(), e);
    }
  }
}
