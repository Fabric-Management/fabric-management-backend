package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.platform.tenant.app.TenantService;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantBusinessCalendarAdapter implements TenantBusinessCalendarPort {
  private final TenantService tenants;

  /**
   * The one place the tenant's business day is resolved: finance postings, collection dates and the
   * day a payment plan is confirmed all use it, so they can never disagree about midnight.
   *
   * @throws IllegalStateException when the tenant has no time zone, so a date is never computed in
   *     a zone nobody chose
   */
  @Override
  public ZoneId zone() {
    String timezone = tenants.getMySettings().getTimezone();
    if (timezone == null || timezone.isBlank())
      throw new IllegalStateException("The tenant has no business time zone configured");
    return ZoneId.of(timezone);
  }
}
