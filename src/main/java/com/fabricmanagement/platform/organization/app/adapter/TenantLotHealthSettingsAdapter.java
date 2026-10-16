package com.fabricmanagement.platform.organization.app.adapter;

import com.fabricmanagement.inventory.lothealth.app.port.LotHealthSettingsPort;
import com.fabricmanagement.inventory.lothealth.app.port.LotHealthSettingsUnavailableException;
import com.fabricmanagement.inventory.lothealth.domain.LotHealthSettings;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import com.fabricmanagement.platform.tenant.domain.TenantSettingsUnreadableException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lot-health thresholds from the tenant's stock-proposal settings (Fatih, 2026-10-14). Settings
 * never set read as the standard; settings that cannot be read stop the caller, because the
 * standard may be looser than what the tenant set.
 */
@Component
@RequiredArgsConstructor
public class TenantLotHealthSettingsAdapter implements LotHealthSettingsPort {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final TenantSystemService tenantSystemService;

  @Override
  public LotHealthSettings forTenant(UUID tenantId) {
    StockProposalSettings settings;
    try {
      settings = tenantSystemService.requireSettings(tenantId).stockProposalOrDefaults();
    } catch (TenantSettingsUnreadableException e) {
      throw new LotHealthSettingsUnavailableException(tenantId, e);
    }
    return new LotHealthSettings(
        settings.minHealthyPiecesMoving(),
        settings.minHealthyPiecesSlowing(),
        settings.minHealthyPiecesStalled(),
        settings.shortPiecePct().divide(HUNDRED));
  }
}
