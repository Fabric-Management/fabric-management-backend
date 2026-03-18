package com.fabricmanagement.flowboard.dashboard.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Dashboard içindeki göstergeler (Widget). */
@Entity
@Table(schema = "flowboard", name = "dashboard_widget")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardWidget extends BaseEntity {

  // [D7 FIX] UUID yerine @ManyToOne ile gerçek FK ilişkisi kuruldu (Flyway migration eklendi)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dashboard_id", nullable = false)
  private DashboardConfig dashboard;

  @Enumerated(EnumType.STRING)
  @Column(name = "widget_type", nullable = false)
  private WidgetType widgetType;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "config_jsonb", columnDefinition = "jsonb")
  private String configJsonb;

  @Column(name = "display_order", nullable = false)
  private int displayOrder = 1;

  public DashboardWidget(
      UUID tenantId,
      DashboardConfig dashboard,
      WidgetType widgetType,
      String title,
      String configJsonb,
      int displayOrder) {
    this.setTenantId(tenantId);
    this.dashboard = dashboard;
    this.widgetType = widgetType;
    this.title = title;
    this.configJsonb = configJsonb;
    this.displayOrder = displayOrder;
  }

  @Override
  protected String getModuleCode() {
    return "DBRD";
  }

  public void updateConfig(String title, String configJsonb, int displayOrder) {
    this.title = title;
    this.configJsonb = configJsonb;
    this.displayOrder = displayOrder;
  }
}
