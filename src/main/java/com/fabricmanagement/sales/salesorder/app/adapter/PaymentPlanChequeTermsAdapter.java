package com.fabricmanagement.sales.salesorder.app.adapter;

import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.sales.salesorder.app.port.PaymentPlanChequeTermsPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tenant switch and customer default for the agreed cheque split (PAYMENT-PLAN-7 R1, R2). Lives in
 * sales because domain modules depend on platform, never the reverse (Rule 11.2).
 */
@Component
@RequiredArgsConstructor
public class PaymentPlanChequeTermsAdapter implements PaymentPlanChequeTermsPort {

  private final TenantSystemService tenantSystemService;
  private final TradingPartnerQueryService tradingPartners;

  @Override
  public boolean chequeSplitTracked(UUID tenantId) {
    return tenantSystemService.requireSettings(tenantId).isChequeSplitTracking();
  }

  @Override
  public int defaultOwnChequePercent(UUID tenantId, UUID partnerId) {
    return tradingPartners
        .findDefaultOwnChequePercent(tenantId, partnerId)
        .orElse(UNSET_DEFAULT_OWN_CHEQUE_PERCENT);
  }
}
