package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerOwnershipSnapshot;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Shared tenant-scoped customer soft-reference validation for commercial ownership. */
@Service
@RequiredArgsConstructor
public class CustomerEligibilityService {

  private final TradingPartnerResolver tradingPartnerResolver;

  public EligibleCustomer requireEligible(UUID tenantId, UUID customerId) {
    TradingPartnerOwnershipSnapshot snapshot =
        tradingPartnerResolver
            .resolveOwnershipSnapshot(tenantId, customerId)
            .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

    if (!snapshot.customer() || !snapshot.transactionAllowed()) {
      throw SalesDomainException.customerNotEligible(customerId.toString());
    }

    return new EligibleCustomer(snapshot.customerId(), snapshot.acquiredById());
  }

  public record EligibleCustomer(UUID customerId, UUID acquiredById) {}
}
