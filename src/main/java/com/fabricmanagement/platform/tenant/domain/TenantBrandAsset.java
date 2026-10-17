package com.fabricmanagement.platform.tenant.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Only validated, self-contained SVG is persisted; external URLs are never accepted here. */
@Entity
@Table(name = "tenant_brand_asset", schema = "common_tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantBrandAsset extends BaseEntity {
  public static final int MAX_BYTES = 512 * 1024;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BrandAssetKind kind;

  @Enumerated(EnumType.STRING)
  @Column(name = "colour_mode", nullable = false, length = 20)
  private BrandColourMode colourMode;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "svg_content", nullable = false, columnDefinition = "text")
  private String svgContent;

  public static TenantBrandAsset create(
      BrandAssetKind kind, BrandColourMode mode, String name, String svg) {
    TenantBrandAsset asset = new TenantBrandAsset();
    asset.kind = java.util.Objects.requireNonNull(kind);
    asset.replace(mode, name, svg);
    return asset;
  }

  public void replace(BrandColourMode mode, String name, String svg) {
    colourMode = java.util.Objects.requireNonNull(mode);
    if (svg == null
        || svg.isBlank()
        || svg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw TenantBrandingDomainException.invalid("SVG must be non-empty and at most 512 KB");
    }
    fileName = name == null ? "brand.svg" : name.replace('\\', '/');
    fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
    if (fileName.isBlank()) fileName = "brand.svg";
    if (fileName.length() > 255) fileName = fileName.substring(0, 255);
    svgContent = svg;
  }

  @Override
  protected String getModuleCode() {
    return "BRND";
  }
}
