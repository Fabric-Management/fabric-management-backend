package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ADR-0014 D2 and D10 at order creation. */
@ExtendWith(MockitoExtension.class)
class SalesOrderCreateRulesTest {

  private static final UUID TENANT = UUID.randomUUID();

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private OrderCreationReplay creationReplay;
  @InjectMocks private SalesOrderService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static CreateSalesOrderRequest request(UUID partnerId, UUID key) {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(partnerId);
    request.setOrderDate(LocalDate.of(2026, 10, 2));
    request.setIdempotencyKey(key);
    return request;
  }

  @Test
  void noOrderIsCreatedWithoutItsCustomerWhoeverCalls() {
    assertThatThrownBy(() -> service.createOrder(request(null, null)))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("customer");
    verifyNoInteractions(orderRepository, partnerResolver);
  }

  @Test
  void aRequestKeyReusedWithOtherContentIsAConflictAndCreatesNothing() {
    UUID customer = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    CreateSalesOrderRequest request = request(customer, key);
    when(partnerResolver.resolvePartnerId(TENANT, customer)).thenReturn(customer);
    when(creationReplay.fingerprint(request)).thenReturn("f");
    when(creationReplay.claim(TENANT, key, "f"))
        .thenThrow(
            OrderDomainException.stage(
                "IDEMPOTENCY_KEY_REUSED", "This request key already created another order"));

    assertThatThrownBy(() -> service.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("another order");
    verify(orderRepository, never()).save(any(SalesOrder.class));
  }
}
