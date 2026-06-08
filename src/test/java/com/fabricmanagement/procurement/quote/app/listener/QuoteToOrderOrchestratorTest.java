package com.fabricmanagement.procurement.quote.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteToOrderOrchestratorTest {

  @Mock private SupplierQuoteRepository quoteRepository;
  @Mock private SupplierRFQRepository rfqRepository;
  @Mock private PurchaseOrderService purchaseOrderService;
  @Mock private PurchaseOrderRepository purchaseOrderRepository;
  @Mock private SubcontractOrderService subcontractOrderService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private QuoteToOrderOrchestrator orchestrator;

  @Mock
  private com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler
      idempotentHandler;

  private UUID tenantId;
  private UUID quoteId;
  private UUID rfqId;
  private SupplierQuoteAcceptedEvent event;
  private SupplierQuote quote;
  private SupplierRFQ rfq;

  @BeforeEach
  void setUp() {
    if (idempotentHandler != null) {
      lenient()
          .doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(3)).run();
                return null;
              })
          .when(idempotentHandler)
          .executeOnce(any(), any(), any(), any());
    }

    tenantId = UUID.randomUUID();
    quoteId = UUID.randomUUID();
    rfqId = UUID.randomUUID();

    event = new SupplierQuoteAcceptedEvent(tenantId, quoteId, rfqId);

    quote = new SupplierQuote();
    quote.setId(quoteId);
    quote.setRfqId(rfqId);
    quote.setQuoteNumber("Q-123");
    quote.setTradingPartnerId(UUID.randomUUID());
    quote.setCurrency("USD");

    rfq = new SupplierRFQ();
    rfq.setId(rfqId);
    rfq.setWorkOrderId(UUID.randomUUID());
  }

  @Test
  void onQuoteAccepted_PurchaseRfq_CreatesPurchaseOrder() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.PURCHASE);

    SupplierQuoteLine quoteLine = new SupplierQuoteLine();
    quoteLine.setRfqLineId(UUID.randomUUID());
    quoteLine.setQty(BigDecimal.TEN);
    quoteLine.setUnitPrice(BigDecimal.valueOf(50));
    quoteLine.setCurrency("USD");
    quoteLine.setUnit("KG");
    quote.setLines(List.of(quoteLine));

    SupplierRFQLine rfqLine = new SupplierRFQLine();
    rfqLine.setId(quoteLine.getRfqLineId());
    rfqLine.setProductId(UUID.randomUUID());
    rfq.setLines(List.of(rfqLine));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsBySupplierQuoteIdAndIsActiveTrue(quoteId)).thenReturn(false);

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(purchaseOrderService).createPurchaseOrder(any(CreatePurchaseOrderRequest.class));
    verifyNoInteractions(subcontractOrderService);
  }

  @Test
  void onQuoteAccepted_SubcontractRfq_CreatesSubcontractOrder() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.SUBCONTRACT);

    SupplierQuoteLine quoteLine = new SupplierQuoteLine();
    quoteLine.setRfqLineId(UUID.randomUUID());
    quoteLine.setQty(BigDecimal.TEN);
    quoteLine.setUnitPrice(BigDecimal.valueOf(50));
    quoteLine.setCurrency("USD");
    quoteLine.setUnit("KG");
    quote.setLines(List.of(quoteLine));

    SupplierRFQLine rfqLine = new SupplierRFQLine();
    rfqLine.setId(quoteLine.getRfqLineId());
    rfqLine.setProductId(UUID.randomUUID());
    rfq.setLines(List.of(rfqLine));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(subcontractOrderService)
        .createSubcontractOrder(any(CreateSubcontractOrderRequest.class));
    verifyNoInteractions(purchaseOrderService);
  }

  @Test
  void onQuoteAccepted_PurchaseOrderAlreadyExists_SkipsCreation() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.PURCHASE);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsBySupplierQuoteIdAndIsActiveTrue(quoteId)).thenReturn(true);

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(purchaseOrderService, never()).createPurchaseOrder(any());
  }

  @Test
  void onQuoteAccepted_QuoteNotFound_DoesNothing() {
    // Arrange
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verifyNoInteractions(rfqRepository);
    verifyNoInteractions(purchaseOrderService);
    verifyNoInteractions(subcontractOrderService);
  }

  @Test
  void onQuoteAccepted_RfqNotFound_DoesNothing() {
    // Arrange
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.empty());

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verifyNoInteractions(purchaseOrderService);
    verifyNoInteractions(subcontractOrderService);
  }

  @Test
  void onQuoteAccepted_PurchaseServiceThrowsException_SwallowsException() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.PURCHASE);
    quote.setLines(List.of());

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsBySupplierQuoteIdAndIsActiveTrue(quoteId)).thenReturn(false);

    doThrow(new RuntimeException("DB down")).when(purchaseOrderService).createPurchaseOrder(any());

    // Act
    // Should not throw
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(purchaseOrderService).createPurchaseOrder(any(CreatePurchaseOrderRequest.class));
  }
}
