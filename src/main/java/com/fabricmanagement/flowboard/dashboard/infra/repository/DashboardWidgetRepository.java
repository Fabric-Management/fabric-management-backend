package com.fabricmanagement.flowboard.dashboard.infra.repository;

import com.fabricmanagement.flowboard.dashboard.domain.DashboardWidget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, UUID> {

  List<DashboardWidget> findByTenantIdAndDashboardIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
      UUID tenantId, UUID dashboardId);
}
