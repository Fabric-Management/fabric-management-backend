package com.fabricmanagement.production.masterdata.color.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tenant-owned color-card master. A color card is a color identity, not a dye lot. */
@Entity
@Table(
    name = "color",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_color_tenant_code",
          columnNames = {"tenant_id", "code"})
    },
    indexes = {
      @Index(name = "idx_color_tenant_active", columnList = "tenant_id, is_active"),
      @Index(name = "idx_color_tenant_code", columnList = "tenant_id, code")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Color extends BaseEntity {

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "color_hex", length = 7)
  private String colorHex;

  public static Color create(java.util.UUID tenantId, String code, String name, String colorHex) {
    Color color =
        Color.builder()
            .code(normalizeCode(code))
            .name(normalizeName(name))
            .colorHex(normalizeHex(colorHex))
            .build();
    color.setTenantId(tenantId);
    color.onCreate();
    return color;
  }

  public void update(String code, String name, String colorHex) {
    this.code = normalizeCode(code);
    this.name = normalizeName(name);
    this.colorHex = normalizeHex(colorHex);
  }

  private static String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Color code must not be blank");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Color name must not be blank");
    }
    return name.trim();
  }

  private static String normalizeHex(String colorHex) {
    if (colorHex == null || colorHex.isBlank()) {
      return null;
    }
    String normalized = colorHex.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("^#[0-9A-F]{6}$")) {
      throw new IllegalArgumentException("Color hex must match #RRGGBB");
    }
    return normalized;
  }

  @Override
  protected String getModuleCode() {
    return "COL";
  }
}
