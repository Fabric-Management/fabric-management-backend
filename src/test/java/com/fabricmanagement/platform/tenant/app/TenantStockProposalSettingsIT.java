package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The general settings and the stock-proposal group are saved by separate endpoints; neither save
 * overwrites the other (Fatih, 2026-10-14). The tenant row stays: saving settings syncs the locale
 * config after commit, as other tenant ITs leave their rows.
 */
class TenantStockProposalSettingsIT extends AbstractIntegrationTest {

  @Autowired private SystemTransactionExecutor system;
  @Autowired private TenantService tenantService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void createTenant() {
    system.executeInTransaction(
        jdbc ->
            jdbc.update(
                """
                INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
                VALUES (?, ?, ?, 'Stock settings tenant', 'ACTIVE')
                """,
                tenantId,
                tenantId.toString(),
                tenantId.toString()));
  }

  @Test
  void aTenantThatNeverSetTheGroup_readsTheDecidedDefaults() {
    StockProposalSettings read =
        TenantContext.executeInTenantContext(
            tenantId, () -> tenantService.getMyStockProposalSettings());

    assertThat(read).isEqualTo(StockProposalSettings.defaults());
  }

  @Test
  void savingTheGroup_keepsTheGeneralSettings_andSavingTheGeneralSettings_keepsTheGroup() {
    StockProposalSettings group =
        new StockProposalSettings(
            new BigDecimal("3"),
            new BigDecimal("7"),
            4,
            6,
            10,
            new BigDecimal("45"),
            60,
            new BigDecimal("75"),
            new BigDecimal("35"),
            150,
            new BigDecimal("8"),
            new BigDecimal("12"),
            9);

    TenantSettings afterBoth =
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              TenantSettings general = TenantSettings.defaults();
              general.setTimezone("Europe/Berlin");
              tenantService.updateMySettings(general);
              tenantService.updateMyStockProposalSettings(group);
              TenantSettings again = tenantService.getMySettings();
              again.setLocale("de-DE");
              again.setStockProposal(null);
              tenantService.updateMySettings(again);
              return tenantService.getMySettings();
            });

    assertThat(afterBoth.getTimezone()).isEqualTo("Europe/Berlin");
    assertThat(afterBoth.getLocale()).isEqualTo("de-DE");
    assertThat(afterBoth.getStockProposal()).isEqualTo(group);
    assertThat(afterBoth.getStockProposal().fabricRollMinHealthyPieces()).isEqualTo(9);
  }
}
