package com.fabricmanagement.platform.organization.app.adapter;

import com.fabricmanagement.costing.app.port.TenantCostingSettingsPort;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantCostingSettingsAdapter implements TenantCostingSettingsPort {

  /** Platform-wide default when tenant has no override. */
  public static final BigDecimal DEFAULT_VARIANCE_THRESHOLD = new BigDecimal("0.10");

  private final TenantSystemService tenantSystemService;

  @Override
  public BigDecimal getVarianceThreshold(UUID tenantId) {
    TenantSettings settings = tenantSystemService.getSettings(tenantId);
    return settings.getCostVarianceThreshold() != null
        ? settings.getCostVarianceThreshold()
        : DEFAULT_VARIANCE_THRESHOLD;
  }
}
