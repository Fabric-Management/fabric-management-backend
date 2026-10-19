package com.fabricmanagement.platform.tenant.domain;

/** Optional dark variants keep multicolour artwork intact on dark surfaces. */
@io.swagger.v3.oas.annotations.media.Schema(enumAsRef = true)
public enum BrandAssetKind {
  LOGO,
  ICON,
  LOGO_DARK,
  ICON_DARK
}
