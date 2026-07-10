package com.fabricmanagement.procurement.quote.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

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
  private final ObjectMapper objectMapper;
  private final IdempotentEventHandler idempotentHandler;
  private final TenantSessionBinder tenantSessionBinder;

  @ApplicationModuleListener
  public void onQuoteAccepted(SupplierQuoteAcceptedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onQuoteAccepted",
        () -> {
          log.info("QuoteToOrderOrchestrator ← QuoteAccepted: quote={}", event.getQuoteId());

          TenantContext.executeInTenantContext(
              event.getTenantId(),
              () -> {
                tenantSessionBinder.bindToCurrentSession(event.getTenantId());
                SupplierQuote quote =
                    quoteRepository
                        .findByTenantIdAndIdAndIsActiveTrue(event.getTenantId(), event.getQuoteId())
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Quote not found: "
                                        + event.getQuoteId()
                                        + ", retrying order creation"));
                SupplierRFQ rfq =
                    rfqRepository
                        .findByTenantIdAndIdAndIsActiveTrue(event.getTenantId(), quote.getRfqId())
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "RFQ not found for quote "
                                        + event.getQuoteId()
                                        + " (rfqId="
                                        + quote.getRfqId()
                                        + "), retrying order creation"));
                routeToOrder(quote, rfq);
              });
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
    if (purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
        quote.getTenantId(), quote.getId())) {
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
                      .productId(matchingRfqLine.map(rl -> rl.getProductId()).orElse(null))
                      .productDesc(matchingRfqLine.map(rl -> rl.getProductDesc()).orElse(null))
                      .qty(line.getQty())
                      .unit(line.getUnit())
                      .unitPrice(line.getUnitPrice())
                      .currency(line.getCurrency())
                      .moduleSpecs(
                          matchingRfqLine
                              .map(
                                  rl -> {
                                    try {
                                      String json =
                                          objectMapper.writeValueAsString(rl.getModuleSpecs());
                                      return objectMapper.readValue(
                                          json,
                                          com.fabricmanagement.procurement.purchaseorder.domain
                                              .specs.PurchaseOrderSpecs.class);
                                    } catch (JsonProcessingException e) {
                                      log.warn(
                                          "Failed to serialize RFQ line moduleSpecs, falling back to generic: {}",
                                          e.getMessage());
                                      return (com.fabricmanagement.procurement.purchaseorder.domain
                                              .specs.PurchaseOrderSpecs)
                                          new com.fabricmanagement.procurement.purchaseorder.domain
                                              .specs.GenericPurchaseSpecs(null);
                                    }
                                  })
                              .orElse(
                                  new com.fabricmanagement.procurement.purchaseorder.domain.specs
                                      .GenericPurchaseSpecs(null)))
                      .build();
                })
            .toList();

    CreatePurchaseOrderRequest poRequest =
        CreatePurchaseOrderRequest.builder()
            .workOrderId(rfq.getWorkOrderId())
            .tradingPartnerId(quote.getTradingPartnerId())
            .supplierQuoteId(quote.getId())
            .moduleType(mapRfqModuleType(rfq.getModuleType()))
            .currency(quote.getCurrency())
            .paymentTerms(quote.getPaymentTerms())
            .notes("Auto-generated from Quote: " + quote.getQuoteNumber())
            .lines(lines)
            .build();

    purchaseOrderService.createPurchaseOrder(poRequest);
    log.info("Successfully created PurchaseOrder for quote {}", quote.getQuoteNumber());
  }

  private void createSubcontractOrder(SupplierQuote quote, SupplierRFQ rfq) {
    if (quote.getLines() == null || quote.getLines().isEmpty()) {
      log.warn(
          "Cannot create SubcontractOrder for quote {} — no lines found", quote.getQuoteNumber());
      return;
    }

    if (quote.getLines().size() > 1) {
      log.warn(
          "Quote {} has {} lines, but SubcontractOrder currently supports 1:1 creation. Processing only the first line.",
          quote.getQuoteNumber(),
          quote.getLines().size());
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
            .inputProductId(matchingRfqLine.map(rl -> rl.getProductId()).orElse(null))
            .productSentQty(firstLine.getQty())
            .agreedUnitPrice(firstLine.getUnitPrice())
            .currency(firstLine.getCurrency())
            .notes("Auto-generated from Subcontract Quote: " + quote.getQuoteNumber())
            .build();

    subcontractOrderService.createSubcontractOrder(soRequest);
    log.info("Successfully created SubcontractOrder for quote {}", quote.getQuoteNumber());
  }

  private PurchaseOrderModuleType mapRfqModuleType(SupplierRFQModuleType rfqType) {
    if (rfqType == null) return PurchaseOrderModuleType.GENERIC;
    return switch (rfqType) {
      case FIBER -> PurchaseOrderModuleType.FIBER;
      case YARN -> PurchaseOrderModuleType.YARN;
      case FABRIC -> PurchaseOrderModuleType.FABRIC;
      case DYE_FINISHING -> PurchaseOrderModuleType.DYE_FINISHING;
      case GENERIC -> PurchaseOrderModuleType.GENERIC;
    };
  }
}
