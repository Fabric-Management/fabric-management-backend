package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.tenant.EmailSandbox;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/** Tenant lifecycle access decisions for common infrastructure guards. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantAccessAdapter implements TenantAccessPort {

  private final TenantSystemService tenantSystemService;

  @Override
  @Cacheable(value = "tenant-writable", key = "#tenantId.toString()")
  public boolean isWritable(UUID tenantId) {
    if (tenantId == null) {
      log.warn("Tenant write decision requested with null tenantId; failing open");
      return true;
    }
    return tenantSystemService
        .findById(tenantId)
        .map(tenant -> tenant.getStatus() == null || tenant.getStatus().canWrite())
        .orElseGet(
            () -> {
              log.warn(
                  "Tenant {} could not be resolved for write decision; failing open", tenantId);
              return true;
            });
  }

  @Override
  @Cacheable(
      value = "tenant-demomode",
      key = "#tenantId.toString()",
      condition = "#tenantId != null")
  public boolean isDemoMode(UUID tenantId) {
    if (tenantId == null) {
      log.warn("Tenant demo-mode decision requested with null tenantId; failing closed");
      return false;
    }
    return tenantSystemService
        .findById(tenantId)
        .map(tenant -> tenant.isDemoMode())
        .orElseGet(
            () -> {
              log.warn(
                  "Tenant {} could not be resolved for demo-mode decision; failing closed",
                  tenantId);
              return false;
            });
  }

  @Override
  @Cacheable(
      value = "tenant-emailsandbox",
      key = "#tenantId.toString()",
      condition = "#tenantId != null")
  public EmailSandbox emailSandbox(UUID tenantId) {
    if (tenantId == null) {
      // No tenant-scoped actor: a platform alert, a boot-time notice. Nothing to sandbox.
      return EmailSandbox.off();
    }
    return tenantSystemService
        .findById(tenantId)
        .map(
            tenant -> {
              if (!tenant.isEmailSandboxed()) {
                return EmailSandbox.off();
              }
              String registrationEmail = tenant.getBillingEmail();
              if (registrationEmail == null || registrationEmail.isBlank()) {
                log.warn(
                    "Tenant {} is email-sandboxed but has no billing_email; its mail will be"
                        + " dropped. Anonymous playground clones never set one.",
                    tenantId);
                return EmailSandbox.withoutRecipient();
              }
              return EmailSandbox.redirectingTo(registrationEmail);
            })
        .orElseGet(
            () -> {
              log.warn(
                  "Tenant {} could not be resolved for email-sandbox decision; failing closed"
                      + " (email will be dropped)",
                  tenantId);
              return EmailSandbox.withoutRecipient();
            });
  }
}
