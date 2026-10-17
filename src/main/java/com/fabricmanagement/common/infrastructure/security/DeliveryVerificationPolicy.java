package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may confirm delivery details (CONSIGNEE-1 C5, C6). Whatever a user without {@code
 * sales:verify-delivery} creates or edits is unverified; whatever a holder creates or edits is
 * verified, the holder owning its correctness. The system (seeders, jobs) counts as a holder.
 *
 * <p>Reads the permissions of the user on the current thread; with no authenticated user the answer
 * is no.
 */
@Component
@RequiredArgsConstructor
public class DeliveryVerificationPolicy {

  private final DataScopeGuard scopeGuard;

  /** True when the current user holds {@code sales:verify-delivery}. */
  public boolean actorMayVerify() {
    try {
      return scopeGuard.currentScope(
              PermissionKey.SALES_VERIFY_DELIVERY.resource(),
              PermissionKey.SALES_VERIFY_DELIVERY.action())
          != null;
    } catch (AccessDeniedException noAuthenticatedUser) {
      return false;
    }
  }

  /** The state of something the current user has just created or edited. */
  public Verification forCurrentActor() {
    return actorMayVerify()
        ? Verification.verifiedBy(TenantContext.getCurrentUserId(), Instant.now())
        : Verification.unverified();
  }
}
