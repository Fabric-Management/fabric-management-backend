package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.tenant.dto.ResetDemoResponse;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantResetService {

  private static final String TENANT_DEMOMODE_CACHE = "tenant-demomode";
  private static final String USERS_BY_TENANT_CACHE = "users-by-tenant";

  private final TenantAccessPort tenantAccessPort;
  private final UserRepository userRepository;
  private final UserContactAssignmentService userContactAssignmentService;
  private final TenantTransactionalPurgeService purgeService;
  private final UserSeeder userSeeder;
  private final DemoTransactionSeeder demoTransactionSeeder;
  private final CacheManager cacheManager;

  /**
   * Resets a demo tenant in the same sequential model as registered demo provisioning: purge under
   * the system transaction, then run the existing seeders. If reseeding fails after purge, the
   * owner can retry because a later reset purges any partial demo_seed rows and reseeds again.
   */
  public ResetDemoResponse reset(AuthenticatedUserContext ctx) {
    if (ctx == null || ctx.tenantId() == null || ctx.userId() == null) {
      throw new PlatformDomainException("User not authenticated", "USER_NOT_AUTHENTICATED", 401);
    }

    UUID tenantId = ctx.tenantId();
    UUID userId = ctx.userId();
    requireDemoMode(tenantId);

    User caller =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "USER_NOT_FOUND", 404));
    ensureOwnerCanReset(caller);
    String ownerEmail = resolveOwnerEmail(tenantId, userId);

    TenantTransactionalPurgeService.PurgeDemoDataResult purgeResult =
        purgeService.purgeDemoData(tenantId);

    int seededPersonaUsers;
    try {
      seededPersonaUsers = userSeeder.seedFor(tenantId, ownerEmail, PersonaSubset.REPRESENTATIVE);
      demoTransactionSeeder.seedFor(tenantId);
    } catch (RuntimeException ex) {
      log.error(
          "Demo reset reseed failed after purge; tenant remains resettable: tenantId={}",
          tenantId,
          ex);
      evictResetCaches(tenantId);
      throw new PlatformDomainException(
          "Demo reset failed while restoring sample data. Please retry reset.",
          "DEMO_RESET_RESEED_FAILED",
          500);
    }

    evictResetCaches(tenantId);
    log.info(
        "Demo reset completed: tenantId={}, userId={}, seededPersonaUsers={}, purgedRows={}",
        tenantId,
        userId,
        seededPersonaUsers,
        purgeResult.deletedRows());
    return new ResetDemoResponse(tenantId, true, seededPersonaUsers, purgeResult.deletedRows());
  }

  private void requireDemoMode(UUID tenantId) {
    boolean demoMode;
    try {
      demoMode = tenantAccessPort.isDemoMode(tenantId);
    } catch (RuntimeException ex) {
      log.warn("Could not resolve demo mode for tenant {}; refusing demo reset", tenantId);
      demoMode = false;
    }
    if (!demoMode) {
      throw new PlatformDomainException(
          "Demo mode is required to reset sample data", "DEMO_MODE_REQUIRED", 403);
    }
  }

  private void ensureOwnerCanReset(User caller) {
    if (caller.isDemoSeed()) {
      throw new PlatformDomainException(
          "Seeded demo users cannot reset demo data", "RESET_REQUIRES_OWNER", 403);
    }
    String roleCode = caller.getRole() != null ? caller.getRole().getRoleCode() : null;
    if (!"ADMIN".equalsIgnoreCase(roleCode) && !"PLATFORM_ADMIN".equalsIgnoreCase(roleCode)) {
      throw new PlatformDomainException(
          "Only a tenant owner or admin can reset demo data", "RESET_REQUIRES_OWNER", 403);
    }
  }

  private String resolveOwnerEmail(UUID tenantId, UUID userId) {
    List<UserContact> contacts =
        TenantContext.executeInTenantContext(
            tenantId, () -> userContactAssignmentService.getUserContacts(userId));
    Comparator<UserContact> defaultFirst =
        Comparator.comparing((UserContact uc) -> Boolean.TRUE.equals(uc.getIsDefault())).reversed();

    return contacts.stream()
        .filter(this::hasEmailContact)
        .filter(uc -> Boolean.TRUE.equals(uc.getContact().getIsVerified()))
        .sorted(defaultFirst)
        .map(uc -> uc.getContact().getContactValue())
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .or(
            () ->
                contacts.stream()
                    .filter(this::hasEmailContact)
                    .filter(uc -> Boolean.TRUE.equals(uc.getIsDefault()))
                    .map(uc -> uc.getContact().getContactValue())
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst())
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "A verified owner email is required to reset demo data",
                    "OWNER_EMAIL_REQUIRED",
                    400));
  }

  private boolean hasEmailContact(UserContact userContact) {
    Contact contact = userContact.getContact();
    return contact != null && ContactType.EMAIL.equals(contact.getContactType());
  }

  private void evictResetCaches(UUID tenantId) {
    evict(TENANT_DEMOMODE_CACHE, tenantId.toString());
    evict(USERS_BY_TENANT_CACHE, tenantId.toString());
  }

  private void evict(String cacheName, String key) {
    var cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.evict(key);
    }
  }
}
