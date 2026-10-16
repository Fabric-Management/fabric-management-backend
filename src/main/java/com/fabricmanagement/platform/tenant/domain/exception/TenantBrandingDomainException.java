package com.fabricmanagement.platform.tenant.domain.exception;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;

public class TenantBrandingDomainException extends PlatformDomainException {
  public TenantBrandingDomainException(String message, String code, int status) {
    super(message, code, status);
  }

  public static TenantBrandingDomainException invalid(String message) {
    return new TenantBrandingDomainException(message, "TENANT_BRANDING_INVALID_SVG", 400);
  }

  public static TenantBrandingDomainException notFound() {
    return new TenantBrandingDomainException(
        "Brand asset not found", "TENANT_BRANDING_NOT_FOUND", 404);
  }
}
