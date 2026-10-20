package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import com.fabricmanagement.sales.quote.domain.*;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteStockOwnershipTransferServiceTest {
  @Mock QuoteRepository quotes;
  @Mock QuoteAccessPolicy access;
  @Mock StockUnitSoftHoldPort holds;
  @Mock QuantityEvaluationService evaluation;
  @Mock DraftStockSelectionRepository selections;
  @Mock EntityManager entityManager;
  final Clock clock = Clock.systemUTC();
  QuoteStockOwnershipTransferService service;
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), customer = UUID.randomUUID();
  Quote quote;
  QuoteLine source;
  SalesOrder order;
  SalesOrderLine target;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenant);
    quote = new Quote();
    quote.setId(UUID.randomUUID());
    quote.setTenantId(tenant);
    quote.setStatus(QuoteStatus.APPROVED);
    quote.setCustomerId(customer);
    source = new QuoteLine();
    source.setId(UUID.randomUUID());
    source.setProductId(UUID.randomUUID());
    source.setRequestedQty(new BigDecimal("700"));
    source.setUnit("M");
    quote.addLine(source);
    order =
        SalesOrder.builder()
            .quoteId(quote.getId())
            .tradingPartnerId(customer)
            .creationKey(UUID.randomUUID())
            .orderNumber("SO-2026-001")
            .build();
    order.setId(UUID.randomUUID());
    target =
        SalesOrderLine.builder()
            .productId(source.getProductId())
            .requestedQty(new BigDecimal("700"))
            .unit("M")
            .build();
    target.setId(UUID.randomUUID());
    service =
        new QuoteStockOwnershipTransferService(
            quotes, access, holds, evaluation, selections, entityManager, clock);
    when(quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quote.getId()))
        .thenReturn(Optional.of(quote));
    when(access.canWrite(tenant, actor, quote, SalesAccessScopeResolver.PermissionFreshness.FRESH))
        .thenReturn(true);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void explicitQuantityOnlySourceTransfersThroughTheProductionPort() {
    when(holds.heldPieceIds(source.getId())).thenReturn(List.of());
    service.prepare(order, actor);
    service.transfer(order, target, source.getId(), actor);
    verify(entityManager, times(2)).refresh(quote, LockModeType.PESSIMISTIC_WRITE);
    verify(holds)
        .transferQuoteHolds(
            quote.getId(), source.getId(), order.getId(), order.getOrderNumber(), target.getId());
    verifyNoInteractions(evaluation, selections);
  }

  @Test
  void anotherCustomerCannotUseTheQuotesStock() {
    order.setTradingPartnerId(UUID.randomUUID());
    assertThatThrownBy(() -> service.transfer(order, target, source.getId(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(holds, evaluation, selections);
  }

  @Test
  void aDraftQuoteCannotBeUsedAsAnApprovedSource() {
    quote.setStatus(QuoteStatus.DRAFT);
    assertThatThrownBy(() -> service.transfer(order, target, source.getId(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(holds, evaluation, selections);
  }

  @Test
  void aProductMatchDoesNotInventAMissingSourceLineMapping() {
    assertThatThrownBy(() -> service.transfer(order, target, UUID.randomUUID(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(holds, evaluation, selections);
  }

  @Test
  void revokedScopeRefusesConversionBeforeTakingTheQuoteLock() {
    when(access.canWrite(tenant, actor, quote, SalesAccessScopeResolver.PermissionFreshness.FRESH))
        .thenReturn(false);
    assertThatThrownBy(() -> service.prepare(order, actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(entityManager, holds, evaluation, selections);
  }
}
