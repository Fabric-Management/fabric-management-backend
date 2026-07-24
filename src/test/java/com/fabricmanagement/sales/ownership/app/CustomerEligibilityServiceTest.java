package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerOwnershipSnapshot;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerEligibilityServiceTest {

  @Mock private TradingPartnerResolver tradingPartnerResolver;
  @InjectMocks private CustomerEligibilityService service;

  @Test
  void returnsCanonicalCustomerAndAcquirerForEligiblePartner() {
    UUID tenantId = UUID.randomUUID();
    UUID requestedId = UUID.randomUUID();
    UUID canonicalId = UUID.randomUUID();
    UUID acquirerId = UUID.randomUUID();
    when(tradingPartnerResolver.resolveOwnershipSnapshot(tenantId, requestedId))
        .thenReturn(
            Optional.of(new TradingPartnerOwnershipSnapshot(canonicalId, acquirerId, true, true)));

    CustomerEligibilityService.EligibleCustomer result =
        service.requireEligible(tenantId, requestedId);

    assertThat(result.customerId()).isEqualTo(canonicalId);
    assertThat(result.acquiredById()).isEqualTo(acquirerId);
  }

  @Test
  void rejectsUnknownOrCrossTenantCustomerAsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    when(tradingPartnerResolver.resolveOwnershipSnapshot(tenantId, customerId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireEligible(tenantId, customerId))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void rejectsSupplierOrInactivePartnerWithLockedCode() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    when(tradingPartnerResolver.resolveOwnershipSnapshot(tenantId, customerId))
        .thenReturn(
            Optional.of(new TradingPartnerOwnershipSnapshot(customerId, null, false, true)));

    assertThatThrownBy(() -> service.requireEligible(tenantId, customerId))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_020_CUSTOMER_NOT_ELIGIBLE"));
  }

  @Test
  void rejectsInactiveCustomerWithLockedCode() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    when(tradingPartnerResolver.resolveOwnershipSnapshot(tenantId, customerId))
        .thenReturn(
            Optional.of(new TradingPartnerOwnershipSnapshot(customerId, null, true, false)));

    assertThatThrownBy(() -> service.requireEligible(tenantId, customerId))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_020_CUSTOMER_NOT_ELIGIBLE"));
  }
}
