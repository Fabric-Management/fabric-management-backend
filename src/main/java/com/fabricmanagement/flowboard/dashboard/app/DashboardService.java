package com.fabricmanagement.flowboard.dashboard.app;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardConfig;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardWidget;
import com.fabricmanagement.flowboard.dashboard.domain.WidgetType;
import com.fabricmanagement.flowboard.dashboard.infra.repository.DashboardConfigRepository;
import com.fabricmanagement.flowboard.dashboard.infra.repository.DashboardWidgetRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final DashboardConfigRepository dashboardRepo;
  private final DashboardWidgetRepository widgetRepo;

  // [D4 FIX] null yerine Optional dönüyor — NPE riski azaltılmış
  @Transactional(readOnly = true)
  public java.util.Optional<DashboardConfig> getDefaultDashboard(UUID tenantId, UUID userId) {
    return dashboardRepo.findByTenantIdAndUserIdAndIsDefaultTrueAndDeletedAtIsNull(
        tenantId, userId);
  }

  @Transactional(readOnly = true)
  public List<DashboardWidget> getDashboardWidgets(UUID tenantId, UUID dashboardId) {
    return widgetRepo.findByTenantIdAndDashboardIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
        tenantId, dashboardId);
  }

  @Transactional
  public DashboardConfig createOrUpdateDefaultDashboard(
      UUID tenantId, UUID userId, String layoutJsonb) {
    DashboardConfig config =
        dashboardRepo
            .findByTenantIdAndUserIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId, userId)
            .orElseGet(
                () -> new DashboardConfig(tenantId, userId, "Genel Bakış", true, layoutJsonb));

    config.updateLayout(layoutJsonb);
    return dashboardRepo.save(config);
  }

  @Transactional
  public DashboardWidget addWidgetToDashboard(
      UUID tenantId,
      UUID dashboardId,
      WidgetType type,
      String title,
      String configJsonb,
      int displayOrder) {

    DashboardConfig dashboard =
        dashboardRepo
            .findById(dashboardId)
            .orElseThrow(
                () ->
                    new FlowBoardDomainException(
                        "Dashboard not found", "FLOWBOARD_DASHBOARD_NOT_FOUND", 404));

    if (!dashboard.getTenantId().equals(tenantId)) {
      throw new FlowBoardDomainException(
          "Dashboard tenant mismatch", "FLOWBOARD_DASHBOARD_TENANT_MISMATCH", 403);
    }

    DashboardWidget widget =
        new DashboardWidget(tenantId, dashboard, type, title, configJsonb, displayOrder);
    return widgetRepo.save(widget);
  }
}
