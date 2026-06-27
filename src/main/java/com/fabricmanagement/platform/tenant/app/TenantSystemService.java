package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.events.TenantSettingsUpdatedEvent;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.domain.event.TenantCreatedEvent;
import com.fabricmanagement.platform.tenant.domain.event.TenantStatusChangedEvent;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tenant.mapper.TenantRowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System-level service for cross-tenant management.
 *
 * <p><b>WARNING:</b> This service executes via {@link SystemTransactionExecutor} using the {@code
 * fabric_system} role (BYPASSRLS). It intentionally bypasses the self-row RLS policy on the tenant
 * table.
 *
 * <p>Access should be strictly limited to:
 *
 * <ul>
 *   <li>Schedulers (e.g., iterating all tenants)
 *   <li>Platform admin operations
 *   <li>Auth flows (resolving tenant before context is set)
 *   <li>Onboarding (creating new tenants)
 * </ul>
 *
 * <p>Do NOT use this service for regular tenant-scoped endpoints. Use {@link TenantService}
 * instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSystemService {

  private static final int TENANT_IDENTITY_INSERT_MAX_ATTEMPTS = 5;

  private final DomainEventPublisher eventPublisher;
  private final SystemTransactionExecutor systemExecutor;
  private final ObjectMapper objectMapper;

  // ========================================
  // CREATE OPERATIONS
  // ========================================

  /**
   * Create a new tenant.
   *
   * @param request Creation request
   * @return Created tenant DTO
   */
  // CR-P4-2: Keep @Transactional to provide a transaction boundary for
  // @TransactionalEventListener(AFTER_COMMIT)
  // in QualityGradeSeedListener. The actual DB insert bypasses this via SystemTransactionExecutor.
  //
  // ⚠ DUAL-TX RISK: The systemExecutor INSERT commits on its own TransactionManager.
  // If the outer @Transactional rolls back (e.g., event listener throws), the INSERT
  // is NOT rolled back → orphan tenant row in DB without seed data.
  // Accepted because: (1) eventPublisher.publish() is synchronous and listeners are
  // @Async @TransactionalEventListener(AFTER_COMMIT), so they run AFTER this method
  // returns successfully; (2) manual cleanup is preferable to cross-DataSource XA.
  @Transactional
  public TenantDto createTenant(CreateTenantRequest request) {
    log.debug("Creating tenant: name={}", request.getName());

    // Prepare settings
    TenantSettings settings = request.getSettings();
    if (settings == null) {
      settings = TenantSettings.defaults();
    }

    // Create tenant via system executor (BYPASSRLS) — tenant table has self-row RLS
    TenantType type = request.getType() != null ? request.getType() : TenantType.REGULAR;
    TenantStatus status = request.getTrialDays() > 0 ? TenantStatus.TRIAL : TenantStatus.ACTIVE;
    Instant trialEndsAt =
        request.getTrialDays() > 0 && !request.isDeferTrialActivation()
            ? Instant.now().plus(java.time.Duration.ofDays(request.getTrialDays()))
            : null;
    String settingsJson = serializeSettings(settings);

    TenantIdentityInsert insert =
        insertTenantWithIdentityRetry(request, type, status, settingsJson, trialEndsAt);
    UUID tenantId = insert.tenantId();
    String uid = insert.uid();

    // CR-1: Set TenantContext BEFORE event publish so that:
    // 1. TenantRestoringEventListenerAspect can propagate the correct tenantId
    // 2. Synchronous @EventListener (AuditService) can read TenantContext.requireTenantId()
    // 3. @Async @TransactionalEventListener (QualityGradeSeedListener) gets context via aspect
    //
    // Wrapped in try/finally to prevent stale context on bootstrap/seeder threads.
    UUID previousTenantId = TenantContext.getCurrentTenantIdOrNull();
    try {
      TenantContext.setCurrentTenantId(tenantId);

      eventPublisher.publish(
          new TenantCreatedEvent(tenantId, uid, request.getName(), status, trialEndsAt));

      log.info("Tenant created: id={}, uid={}, status={}", tenantId, uid, status);

      // Return DTO via system executor (self-row RLS blocks fabric_app from seeing other tenants)
      return findByIdSystem(tenantId)
          .orElseThrow(
              () -> new IllegalStateException("Tenant just created but not found: " + tenantId));
    } finally {
      if (previousTenantId != null) {
        TenantContext.setCurrentTenantId(previousTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }

  private record TenantIdentityInsert(UUID tenantId, String uid) {}

  private TenantIdentityInsert insertTenantWithIdentityRetry(
      CreateTenantRequest request,
      TenantType type,
      TenantStatus status,
      String settingsJson,
      Instant trialEndsAt) {
    for (int attempt = 1; attempt <= TENANT_IDENTITY_INSERT_MAX_ATTEMPTS; attempt++) {
      String uid = generateUid(request.getName());
      String slug = generateUniqueSlug(request.getName());
      try {
        UUID tenantId = insertTenant(request, type, status, settingsJson, trialEndsAt, uid, slug);
        return new TenantIdentityInsert(tenantId, uid);
      } catch (DataIntegrityViolationException e) {
        if (!isTenantIdentityCollision(e) || attempt == TENANT_IDENTITY_INSERT_MAX_ATTEMPTS) {
          throw e;
        }
        log.warn(
            "Tenant identity collision while creating tenant '{}', retrying ({}/{})",
            request.getName(),
            attempt,
            TENANT_IDENTITY_INSERT_MAX_ATTEMPTS);
      }
    }
    throw new IllegalStateException("Tenant identity retry loop exhausted unexpectedly");
  }

  private UUID insertTenant(
      CreateTenantRequest request,
      TenantType type,
      TenantStatus status,
      String settingsJson,
      Instant trialEndsAt,
      String uid,
      String slug) {
    return systemExecutor.executeInTransaction(
        jdbc -> {
          UUID id = UUID.randomUUID();
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant "
                  + "(id, uid, slug, name, type, status, settings, billing_email, "
                  + "trial_ends_at, demo_mode, is_active, created_at, updated_at, version) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, true, now(), now(), 0)",
              id,
              uid,
              slug,
              request.getName(),
              type.name(),
              status.name(),
              settingsJson,
              request.getBillingEmail(),
              trialEndsAt != null ? java.sql.Timestamp.from(trialEndsAt) : null,
              request.isDemoMode());
          return id;
        });
  }

  private boolean isTenantIdentityCollision(DataIntegrityViolationException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null
          && (message.contains("uk_tenant_uid") || message.contains("uk_tenant_slug"))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  // ========================================
  // READ OPERATIONS (CR-3: All via SystemTransactionExecutor — BYPASSRLS)
  // Tenant table has self-row RLS (id = current_setting). JPA queries through
  // fabric_app would only see the current tenant's own row. Platform admin,
  // onboarding, and scheduler flows need cross-tenant visibility.
  // ========================================

  /**
   * Find tenant by ID.
   *
   * @param id Tenant UUID
   * @return Tenant if found
   */
  public Optional<TenantDto> findById(UUID id) {
    return findByIdSystem(id);
  }

  /**
   * Find active tenant by ID.
   *
   * @param id Tenant UUID
   * @return Tenant if found and active
   */
  public Optional<TenantDto> findActiveById(UUID id) {
    return systemExecutor
        .executeQuery(
            "SELECT * FROM common_tenant.common_tenant WHERE id = ? AND is_active = true",
            TenantRowMapper.INSTANCE,
            id)
        .stream()
        .findFirst();
  }

  /**
   * Find tenant by UID.
   *
   * @param uid Human-readable UID (e.g., "ACME-001")
   * @return Tenant if found
   */
  public Optional<TenantDto> findByUid(String uid) {
    return systemExecutor
        .executeQuery(
            "SELECT * FROM common_tenant.common_tenant WHERE uid = ?",
            TenantRowMapper.INSTANCE,
            uid)
        .stream()
        .findFirst();
  }

  /**
   * Find tenant by slug.
   *
   * @param slug URL-friendly slug (e.g., "acme-corp")
   * @return Tenant if found
   */
  public Optional<TenantDto> findBySlug(String slug) {
    return systemExecutor
        .executeQuery(
            "SELECT * FROM common_tenant.common_tenant WHERE slug = ?",
            TenantRowMapper.INSTANCE,
            slug)
        .stream()
        .findFirst();
  }

  /**
   * Get all active tenants.
   *
   * @return List of active tenants
   */
  public List<TenantDto> getAllActive() {
    return systemExecutor.executeQuery(
        "SELECT * FROM common_tenant.common_tenant WHERE is_active = true ORDER BY created_at DESC",
        TenantRowMapper.INSTANCE);
  }

  /**
   * Get tenants by status.
   *
   * @param status Tenant status
   * @return List of tenants with given status
   */
  public List<TenantDto> getByStatus(TenantStatus status) {
    return systemExecutor.executeQuery(
        "SELECT * FROM common_tenant.common_tenant WHERE status = ? ORDER BY created_at DESC",
        TenantRowMapper.INSTANCE,
        status.name());
  }

  // ========================================
  // SETTINGS OPERATIONS
  // ========================================

  /**
   * Get settings for a tenant.
   *
   * <p>Returns default settings when the tenant record is not found or settings are null. This
   * ensures the GET endpoint never fails with 4xx/5xx for a missing or incomplete record.
   *
   * @param tenantId Tenant UUID
   * @return TenantSettings (never null)
   */
  public TenantSettings getSettings(UUID tenantId) {
    try {
      String settingsJson =
          systemExecutor.executeQueryForObject(
              "SELECT settings FROM common_tenant.common_tenant WHERE id = ?",
              (rs, i) -> rs.getString("settings"),
              tenantId);
      if (settingsJson == null || settingsJson.isBlank()) {
        log.warn("Tenant not found or settings null for: {}. Returning defaults.", tenantId);
        return TenantSettings.defaults();
      }
      return objectMapper.readValue(settingsJson, TenantSettings.class);
    } catch (Exception e) {
      log.error(
          "Unexpected error loading settings for tenant {}: {}. Returning defaults.",
          tenantId,
          e.getMessage(),
          e);
      return TenantSettings.defaults();
    }
  }

  /**
   * Update settings for a tenant.
   *
   * @param tenantId Tenant UUID
   * @param settings New settings to apply (merged with existing)
   * @return Updated TenantSettings
   */
  public TenantSettings updateSettings(UUID tenantId, TenantSettings settings) {
    String settingsJson = serializeSettings(settings);
    int updated =
        systemExecutor.executeInTransaction(
            jdbc ->
                jdbc.update(
                    "UPDATE common_tenant.common_tenant SET settings = ?::jsonb, updated_at = now(), version = version + 1 WHERE id = ?",
                    settingsJson,
                    tenantId));

    if (updated == 0) {
      throw new IllegalArgumentException("Tenant not found: " + tenantId);
    }

    eventPublisher.publish(
        new TenantSettingsUpdatedEvent(
            tenantId, settings.getTimezone(), settings.getLocale(), settings.getCurrency()));

    log.info("Tenant settings updated: id={}", tenantId);
    return settings;
  }

  // ========================================
  // STATUS OPERATIONS
  // ========================================

  /**
   * Activate tenant subscription.
   *
   * @param tenantId Tenant UUID
   * @param plan Subscription plan name
   * @return Updated tenant
   */
  @CacheEvict(
      value = {"tenant-writable", "tenant-demomode"},
      key = "#tenantId.toString()")
  public TenantDto activate(UUID tenantId, String plan) {
    return updateStatusSystem(tenantId, TenantStatus.ACTIVE, "Subscription activated: " + plan);
  }

  /**
   * Suspend tenant.
   *
   * @param tenantId Tenant UUID
   * @param reason Suspension reason
   * @return Updated tenant
   */
  @CacheEvict(
      value = {"tenant-writable", "tenant-demomode"},
      key = "#tenantId.toString()")
  public TenantDto suspend(UUID tenantId, String reason) {
    return updateStatusSystem(tenantId, TenantStatus.SUSPENDED, reason);
  }

  /**
   * Cancel tenant subscription permanently.
   *
   * @param tenantId Tenant UUID
   * @param reason Cancellation reason
   * @return Updated tenant
   */
  @CacheEvict(
      value = {"tenant-writable", "tenant-demomode"},
      key = "#tenantId.toString()")
  public TenantDto cancel(UUID tenantId, String reason) {
    return updateStatusSystem(tenantId, TenantStatus.CANCELLED, reason);
  }

  /**
   * Atomic status transition via system executor (BYPASSRLS).
   *
   * <p>All operations (read previous status, update, read uid) are executed within a single
   * transaction to prevent TOCTOU race conditions when concurrent status updates arrive.
   */
  private TenantDto updateStatusSystem(UUID tenantId, TenantStatus newStatus, String reason) {
    record StatusTransition(TenantStatus previousStatus, String uid) {}

    StatusTransition transition =
        systemExecutor.executeInTransaction(
            jdbc -> {
              // 1. Read current status + uid in same TX
              var row =
                  jdbc.queryForMap(
                      "SELECT status, uid FROM common_tenant.common_tenant WHERE id = ?", tenantId);
              if (row == null || row.isEmpty()) {
                throw new IllegalArgumentException("Tenant not found: " + tenantId);
              }
              TenantStatus previousStatus = TenantStatus.valueOf((String) row.get("status"));
              String uid = (String) row.get("uid");

              // 2. Update status atomically within the same TX
              jdbc.update(
                  "UPDATE common_tenant.common_tenant SET status = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                  newStatus.name(),
                  tenantId);

              return new StatusTransition(previousStatus, uid);
            });

    eventPublisher.publish(
        new TenantStatusChangedEvent(
            tenantId, transition.uid(), transition.previousStatus(), newStatus, reason));

    log.info(
        "Tenant status updated: id={}, {} → {}, reason={}",
        tenantId,
        transition.previousStatus(),
        newStatus,
        reason);

    return findByIdSystem(tenantId)
        .orElseThrow(
            () -> new IllegalStateException("Tenant not found after status update: " + tenantId));
  }

  // ========================================
  // UTILITY METHODS
  // ========================================

  /**
   * Generate unique UID from name.
   *
   * <p>Format: {PREFIX}-{SEQUENCE}, e.g., "ACME-001", "AKKAYALAR-002"
   *
   * @param name Tenant name
   * @return Unique UID
   */
  private String generateUid(String name) {
    if (name == null || name.isBlank()) {
      return "TENANT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    String[] words = name.trim().split("\\s+");
    String base = words[0].toUpperCase().replaceAll("[^A-Z0-9]", "");
    String prefix = base.isEmpty() ? "TENANT" : base.substring(0, Math.min(10, base.length()));

    int counter = 1;
    String candidateUid;
    do {
      candidateUid = String.format("%s-%03d", prefix, counter);
      if (!existsByUidSystem(candidateUid)) {
        break;
      }
      counter++;
      if (counter > 999) {
        String uuidSuffix =
            UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        candidateUid = String.format("%s-%s", prefix, uuidSuffix);
        log.warn(
            "Too many UID collisions for prefix {}, using UUID suffix: {}", prefix, candidateUid);
        break;
      }
    } while (counter <= 999);

    return candidateUid;
  }

  /**
   * Generate a unique URL-friendly slug from name. Appends numeric suffix on collision.
   *
   * @param name Tenant/company name
   * @return unique slug guaranteed not to exist in the database
   */
  private String generateUniqueSlug(String name) {
    String baseSlug;
    if (name == null || name.isBlank()) {
      baseSlug = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
    } else {
      baseSlug =
          name.toLowerCase()
              .trim()
              .replaceAll("[^a-z0-9\\s-]", "")
              .replaceAll("\\s+", "-")
              .replaceAll("-+", "-")
              .replaceAll("^-|-$", "");
      if (baseSlug.isEmpty()) {
        baseSlug = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
      }
    }

    if (!existsBySlugSystem(baseSlug)) {
      return baseSlug;
    }

    int counter = 2;
    String candidateSlug;
    do {
      candidateSlug = baseSlug + "-" + counter;
      counter++;
      if (counter > 999) {
        String uuidSuffix =
            UUID.randomUUID().toString().replace("-", "").substring(0, 6).toLowerCase();
        candidateSlug = baseSlug + "-" + uuidSuffix;
        log.warn(
            "Too many slug collisions for '{}', using UUID suffix: {}", baseSlug, candidateSlug);
        break;
      }
    } while (existsBySlugSystem(candidateSlug));

    return candidateSlug;
  }

  /**
   * Check if tenant exists.
   *
   * @param tenantId Tenant UUID
   * @return true if tenant exists
   */
  public boolean exists(UUID tenantId) {
    Integer count =
        systemExecutor.executeQueryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE id = ?",
            (rs, i) -> rs.getInt(1),
            tenantId);
    return count != null && count > 0;
  }

  // ========================================
  // MIGRATION / SYNC
  // ========================================

  /**
   * Manually trigger settings synchronization for all tenants. Usually invoked via admin endpoints
   * for one-time migrations.
   *
   * @return Number of tenants synchronized
   */
  public int syncAllTenantSettings() {
    List<TenantDto> tenants = getAllActive();
    int count = 0;
    for (TenantDto t : tenants) {
      TenantSettings settings = getSettings(t.getId());
      if (settings != null) {
        eventPublisher.publish(
            new TenantSettingsUpdatedEvent(
                t.getId(), settings.getTimezone(), settings.getLocale(), settings.getCurrency()));
        count++;
      }
    }
    log.info("Synced settings for {} tenants to downstream modules", count);
    return count;
  }

  // ========================================
  // SYSTEM EXECUTOR HELPERS (BYPASSRLS)
  // ========================================

  private boolean existsByUidSystem(String uid) {
    Integer count =
        systemExecutor.executeQueryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE uid = ?",
            (rs, i) -> rs.getInt(1),
            uid);
    return count != null && count > 0;
  }

  private boolean existsBySlugSystem(String slug) {
    Integer count =
        systemExecutor.executeQueryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE slug = ?",
            (rs, i) -> rs.getInt(1),
            slug);
    return count != null && count > 0;
  }

  /** CR-3: Reusable system-level find by ID. */
  private Optional<TenantDto> findByIdSystem(UUID id) {
    return systemExecutor
        .executeQuery(
            "SELECT * FROM common_tenant.common_tenant WHERE id = ?", TenantRowMapper.INSTANCE, id)
        .stream()
        .findFirst();
  }

  /** CR-12: Use injected ObjectMapper instead of creating a new one per call. */
  private String serializeSettings(TenantSettings settings) {
    try {
      return objectMapper.writeValueAsString(settings);
    } catch (Exception e) {
      log.warn("Failed to serialize tenant settings, using empty JSON: {}", e.getMessage());
      return "{}";
    }
  }
}
