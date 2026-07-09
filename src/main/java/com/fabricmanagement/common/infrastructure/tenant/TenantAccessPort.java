package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/** Cross-module access decision port for tenant lifecycle gates. */
public interface TenantAccessPort {

  /**
   * Returns whether the tenant can perform write operations.
   *
   * <p>Implementations fail open for unresolved tenants.
   */
  boolean isWritable(UUID tenantId);

  /**
   * Returns whether the tenant is currently in registered playground/demo mode.
   *
   * <p>Implementations fail closed for unresolved tenants.
   */
  boolean isDemoMode(UUID tenantId);

  /**
   * Returns the tenant's email-sandbox state.
   *
   * <p>A {@code null} tenantId means no tenant-scoped actor triggered this email — a platform
   * alert, say — and the sandbox is off. But a tenantId that <b>cannot be resolved</b> fails
   * closed: it is sandboxed with no redirect address, so the mail is dropped. A tenant we cannot
   * identify is one we cannot vouch for, and a wrongly-dropped email costs a missing notification
   * while a wrongly-sent one costs a stranger receiving mail from our domain.
   */
  EmailSandbox emailSandbox(UUID tenantId);
}
