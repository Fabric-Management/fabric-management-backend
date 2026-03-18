package com.fabricmanagement.flowboard.dashboard.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Özelleştirilebilir Dashboard yerleşimi. */
@Entity
@Table(schema = "flowboard", name = "dashboard_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardConfig extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault = false;

  @Column(name = "layout_jsonb", columnDefinition = "jsonb")
  private String layoutJsonb;

  public DashboardConfig(
      UUID tenantId, UUID userId, String name, boolean isDefault, String layoutJsonb) {
    this.setTenantId(tenantId);
    this.userId = userId;
    this.name = name;
    this.isDefault = isDefault;
    this.layoutJsonb = layoutJsonb;
  }

  @Override
  protected String getModuleCode() {
    return "DBRD";
  }

  public void updateLayout(String layoutJsonb) {
    this.layoutJsonb = layoutJsonb;
  }

  public void updateName(String name) {
    this.name = name;
  }
}
