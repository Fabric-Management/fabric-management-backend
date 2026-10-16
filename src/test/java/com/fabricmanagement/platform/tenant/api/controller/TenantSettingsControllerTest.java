package com.fabricmanagement.platform.tenant.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.StockProposalSettings;
import com.fabricmanagement.platform.tenant.dto.UpdateStockProposalSettingsRequest;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import com.fabricmanagement.platform.tenant.mapper.TenantSettingsMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** Only administrators change tenant settings (Fatih, 2026-10-14); everyone reads them. */
class TenantSettingsControllerTest {

  private final TenantService service = mock(TenantService.class);
  private final TenantSettingsMapper mapper = mock(TenantSettingsMapper.class);
  private final SpELPermissionEvaluator auth = mock(SpELPermissionEvaluator.class);
  private final Authentication user = mock(Authentication.class);
  private final TenantSettingsController controller =
      new TenantSettingsController(service, mapper, auth);

  @Test
  void aUserWhoCannotManageMembers_cannotChangeTheGeneralSettings() {
    when(auth.can(user, "members", "manage")).thenReturn(false);

    assertThatThrownBy(() -> controller.updateSettings(generalRequest(), user))
        .isInstanceOf(AccessDeniedException.class);
    verify(service, never()).updateMySettings(any());
  }

  @Test
  void aUserWhoCannotManageMembers_cannotChangeTheStockProposalSettings() {
    when(auth.can(user, "members", "manage")).thenReturn(false);

    assertThatThrownBy(() -> controller.updateStockProposalSettings(stockRequest(), user))
        .isInstanceOf(AccessDeniedException.class);
    verify(service, never()).updateMyStockProposalSettings(any());
  }

  @Test
  void anAdministrator_changesTheStockProposalSettings() {
    when(auth.can(user, "members", "manage")).thenReturn(true);
    when(service.updateMyStockProposalSettings(any())).thenAnswer(call -> call.getArgument(0));

    var response = controller.updateStockProposalSettings(stockRequest(), user);

    assertThat(response.getBody().getData().minHealthyPiecesStalled()).isEqualTo(9);
    verify(service).updateMyStockProposalSettings(stockRequest().toSettings());
  }

  @Test
  void everyUserReadsTheStockProposalSettings_withoutAPermissionCheck() {
    when(service.getMyStockProposalSettings()).thenReturn(StockProposalSettings.defaults());

    var response = controller.getStockProposalSettings();

    assertThat(response.getBody().getData().toleranceDownPct()).isEqualByComparingTo("5");
    verify(auth, never()).can(any(), any(), any());
  }

  private static UpdateTenantSettingsRequest generalRequest() {
    return new UpdateTenantSettingsRequest(
        "Europe/London",
        "en-GB",
        "GBP",
        "GB",
        null,
        false,
        true,
        true,
        null,
        null,
        false,
        480,
        null);
  }

  private static UpdateStockProposalSettingsRequest stockRequest() {
    return new UpdateStockProposalSettingsRequest(
        new BigDecimal("5"),
        new BigDecimal("5"),
        3,
        6,
        9,
        new BigDecimal("51"),
        90,
        new BigDecimal("80"),
        new BigDecimal("40"),
        180,
        new BigDecimal("10"),
        new BigDecimal("20"));
  }
}
