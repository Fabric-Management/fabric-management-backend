package com.fabricmanagement.platform.organization.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.inventory.lothealth.app.port.LotHealthSettingsUnavailableException;
import com.fabricmanagement.inventory.lothealth.domain.LotHealthPolicy;
import com.fabricmanagement.inventory.lothealth.domain.LotHealthSettings;
import com.fabricmanagement.inventory.lothealth.domain.ProductSpeed;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantSettingsUnreadableException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantLotHealthSettingsAdapterTest {

  @Mock private TenantSystemService tenantSystemService;
  @InjectMocks private TenantLotHealthSettingsAdapter adapter;

  private final UUID tenantId = UUID.randomUUID();

  @Test
  void aTenantWithoutItsOwnThresholds_getsTheStandard() {
    when(tenantSystemService.requireSettings(tenantId)).thenReturn(TenantSettings.defaults());

    LotHealthSettings settings = adapter.forTenant(tenantId);

    assertThat(settings.policyFor(ProductSpeed.MOVING)).isEqualTo(LotHealthPolicy.STANDARD);
    assertThat(settings.minHealthyPiecesSlowing()).isEqualTo(5);
    assertThat(settings.minHealthyPiecesStalled()).isEqualTo(8);
  }

  @Test
  void settingsThatCannotBeRead_stopTheCaller_neverFallBackToTheStandard() {
    // The tenant may have set a stricter minimum than three; proposing on three would break it.
    when(tenantSystemService.requireSettings(tenantId))
        .thenThrow(new TenantSettingsUnreadableException(tenantId, new RuntimeException("db")));

    assertThatThrownBy(() -> adapter.forTenant(tenantId))
        .isInstanceOf(LotHealthSettingsUnavailableException.class);
  }

  @Test
  void theTenantsThresholdsAreUsed_thePercentAsARatio() {
    TenantSettings stored = TenantSettings.defaults();
    stored.setStockProposal(
        new StockProposalSettings(
            null, null, 4, 6, 10, new BigDecimal("45"), null, null, null, null, null, null));
    when(tenantSystemService.requireSettings(tenantId)).thenReturn(stored);

    LotHealthSettings settings = adapter.forTenant(tenantId);

    assertThat(settings.minHealthyPiecesMoving()).isEqualTo(4);
    assertThat(settings.minHealthyPiecesSlowing()).isEqualTo(6);
    assertThat(settings.minHealthyPiecesStalled()).isEqualTo(10);
    assertThat(settings.shortPieceRatio()).isEqualByComparingTo("0.45");
  }
}
