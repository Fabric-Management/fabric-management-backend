package com.fabricmanagement.flowboard.dashboard.api.mapper;

import com.fabricmanagement.flowboard.dashboard.domain.DashboardConfig;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardWidget;
import com.fabricmanagement.flowboard.dashboard.dto.DashboardConfigDto;
import com.fabricmanagement.flowboard.dashboard.dto.DashboardWidgetDto;

public class DashboardMapper {

  private DashboardMapper() {}

  public static DashboardConfigDto toDto(DashboardConfig entity) {
    if (entity == null) {
      return null;
    }
    return new DashboardConfigDto(
        entity.getId(),
        entity.getUserId(),
        entity.getName(),
        entity.isDefault(),
        entity.getLayoutJsonb(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public static DashboardWidgetDto toDto(DashboardWidget entity) {
    if (entity == null) {
      return null;
    }
    return new DashboardWidgetDto(
        entity.getId(),
        entity.getDashboard().getId(),
        entity.getWidgetType(),
        entity.getTitle(),
        entity.getConfigJsonb(),
        entity.getDisplayOrder(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
