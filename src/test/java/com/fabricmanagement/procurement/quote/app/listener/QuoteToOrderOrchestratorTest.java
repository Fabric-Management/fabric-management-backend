package com.fabricmanagement.procurement.quote.app.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderConstraintViolationMatcher;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class QuoteToOrderOrchestratorTest {

  @Mock private SupplierQuoteRepository quoteRepository;
  @Mock private SupplierRFQRepository rfqRepository;
  @Mock private PurchaseOrderCreationTransaction purchaseOrderCreationTransaction;
  @Mock private PurchaseOrderRepository purchaseOrderRepository;
  @Mock private SubcontractOrderService subcontractOrderService;
  @Mock private TenantSessionBinder tenantSessionBinder;
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
    quote.setTenantId(tenantId);
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
    when(purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
            tenantId, quoteId))
        .thenReturn(false);

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(purchaseOrderCreationTransaction).createAndFlush(any(CreatePurchaseOrderRequest.class));
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
    verifyNoInteractions(purchaseOrderCreationTransaction);
  }

  @Test
  void onQuoteAccepted_PurchaseOrderAlreadyExists_SkipsCreation() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.PURCHASE);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
            tenantId, quoteId))
        .thenReturn(true);

    // Act
    orchestrator.onQuoteAccepted(event);

    // Assert
    verify(purchaseOrderCreationTransaction, never()).createAndFlush(any());
  }

  @Test
  void onQuoteAccepted_QuoteNotFound_ThrowsForRetry() {
    // Arrange
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    // Act / Assert
    assertThatThrownBy(() -> orchestrator.onQuoteAccepted(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Quote not found");

    verifyNoInteractions(rfqRepository);
    verifyNoInteractions(purchaseOrderCreationTransaction);
    verifyNoInteractions(subcontractOrderService);
  }

  @Test
  void onQuoteAccepted_RfqNotFound_ThrowsForRetry() {
    // Arrange
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.empty());

    // Act / Assert
    assertThatThrownBy(() -> orchestrator.onQuoteAccepted(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RFQ not found");

    verifyNoInteractions(purchaseOrderCreationTransaction);
    verifyNoInteractions(subcontractOrderService);
  }

  @Test
  void onQuoteAccepted_PurchaseServiceThrowsException_PropagatesForRetry() {
    // Arrange
    rfq.setRfqType(SupplierRFQType.PURCHASE);
    quote.setLines(List.of());

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
            tenantId, quoteId))
        .thenReturn(false);

    doThrow(new RuntimeException("DB down"))
        .when(purchaseOrderCreationTransaction)
        .createAndFlush(any());

    // Act / Assert
    assertThatThrownBy(() -> orchestrator.onQuoteAccepted(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("DB down");

    verify(purchaseOrderCreationTransaction).createAndFlush(any(CreatePurchaseOrderRequest.class));
  }

  @Test
  void onQuoteAccepted_PurchaseOrderConstraintRace_CompletesWithoutRetry() {
    rfq.setRfqType(SupplierRFQType.PURCHASE);
    quote.setLines(List.of());
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
            tenantId, quoteId))
        .thenReturn(false);
    doThrow(
            new DataIntegrityViolationException(
                "duplicate "
                    + PurchaseOrderConstraintViolationMatcher.ACTIVE_SUPPLIER_QUOTE_CONSTRAINT))
        .when(purchaseOrderCreationTransaction)
        .createAndFlush(any());

    assertThatCode(() -> orchestrator.onQuoteAccepted(event)).doesNotThrowAnyException();

    verify(purchaseOrderCreationTransaction).createAndFlush(any());
  }

  @Test
  void onQuoteAccepted_UnrelatedIntegrityViolation_PropagatesForRetry() {
    rfq.setRfqType(SupplierRFQType.PURCHASE);
    quote.setLines(List.of());
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(rfqRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, rfqId))
        .thenReturn(Optional.of(rfq));
    when(purchaseOrderRepository.existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
            tenantId, quoteId))
        .thenReturn(false);
    DataIntegrityViolationException unrelated =
        new DataIntegrityViolationException("ck_purchase_order_status");
    doThrow(unrelated).when(purchaseOrderCreationTransaction).createAndFlush(any());

    assertThatThrownBy(() -> orchestrator.onQuoteAccepted(event)).isSameAs(unrelated);
  }
}
