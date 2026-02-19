package com.fabricmanagement.common.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.tenant.domain.Tenant;
import com.fabricmanagement.common.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.common.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.common.platform.tenant.domain.event.TenantCreatedEvent;
import com.fabricmanagement.common.platform.tenant.domain.event.TenantStatusChangedEvent;
import com.fabricmanagement.common.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.common.platform.tenant.dto.TenantDto;
import com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Tenant management.
 *
 * <p><b>IMPORTANT:</b> This is a PLATFORM-LEVEL service. Unlike other services that operate within
 * a tenant context, this service manages tenants themselves.
 *
 * <p>Access should be restricted to:
 *
 * <ul>
 *   <li>Platform admin endpoints
 *   <li>Onboarding flow
 *   <li>Billing/subscription webhooks
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final TenantRepository tenantRepository;
  private final DomainEventPublisher eventPublisher;

  // ========================================
  // CREATE OPERATIONS
  // ========================================

  /**
   * Create a new tenant.
   *
   * @param request Creation request
   * @return Created tenant DTO
   */
  @Transactional
  public TenantDto createTenant(CreateTenantRequest request) {
    log.debug("Creating tenant: name={}", request.getName());

    // Generate UID
    String uid = generateUid(request.getName());

    // Prepare settings
    TenantSettings settings = request.getSettings();
    if (settings == null) {
      settings =
          "TR".equalsIgnoreCase(request.getCountry())
              ? TenantSettings.forTurkey()
              : TenantSettings.defaults();
    }

    // Generate collision-safe slug
    String slug = generateUniqueSlug(request.getName());

    // Create tenant
    Tenant tenant = Tenant.create(request.getName(), uid, slug, settings);
    tenant.setBillingEmail(request.getBillingEmail());

    // Start trial if requested
    if (request.getTrialDays() > 0) {
      tenant.startTrial(request.getTrialDays());
    }

    Tenant saved = tenantRepository.save(tenant);

    // Publish event
    eventPublisher.publish(
        new TenantCreatedEvent(
            saved.getId(),
            saved.getUid(),
            saved.getName(),
            saved.getStatus(),
            saved.getTrialEndsAt()));

    log.info(
        "Tenant created: id={}, uid={}, status={}",
        saved.getId(),
        saved.getUid(),
        saved.getStatus());
    return TenantDto.from(saved);
  }

  // ========================================
  // READ OPERATIONS
  // ========================================

  /**
   * Find tenant by ID.
   *
   * @param id Tenant UUID
   * @return Tenant if found
   */
  @Transactional(readOnly = true)
  public Optional<TenantDto> findById(UUID id) {
    return tenantRepository.findById(id).map(TenantDto::from);
  }

  /**
   * Find active tenant by ID.
   *
   * @param id Tenant UUID
   * @return Tenant if found and active
   */
  @Transactional(readOnly = true)
  public Optional<TenantDto> findActiveById(UUID id) {
    return tenantRepository.findActiveById(id).map(TenantDto::from);
  }

  /**
   * Find tenant by UID.
   *
   * @param uid Human-readable UID (e.g., "ACME-001")
   * @return Tenant if found
   */
  @Transactional(readOnly = true)
  public Optional<TenantDto> findByUid(String uid) {
    return tenantRepository.findByUid(uid).map(TenantDto::from);
  }

  /**
   * Find tenant by slug.
   *
   * @param slug URL-friendly slug (e.g., "acme-corp")
   * @return Tenant if found
   */
  @Transactional(readOnly = true)
  public Optional<TenantDto> findBySlug(String slug) {
    return tenantRepository.findBySlug(slug).map(TenantDto::from);
  }

  /**
   * Get all active tenants.
   *
   * @return List of active tenants
   */
  @Transactional(readOnly = true)
  public List<TenantDto> getAllActive() {
    return tenantRepository.findAllActive().stream().map(TenantDto::from).toList();
  }

  /**
   * Get tenants by status.
   *
   * @param status Tenant status
   * @return List of tenants with given status
   */
  @Transactional(readOnly = true)
  public List<TenantDto> getByStatus(TenantStatus status) {
    return tenantRepository.findByStatus(status).stream().map(TenantDto::from).toList();
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
  @Transactional
  public TenantDto activate(UUID tenantId, String plan) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    TenantStatus previousStatus = tenant.getStatus();
    tenant.activate(plan);
    Tenant saved = tenantRepository.save(tenant);

    eventPublisher.publish(
        new TenantStatusChangedEvent(
            saved.getId(),
            saved.getUid(),
            previousStatus,
            saved.getStatus(),
            "Subscription activated: " + plan));

    log.info("Tenant activated: id={}, plan={}", tenantId, plan);
    return TenantDto.from(saved);
  }

  /**
   * Suspend tenant.
   *
   * @param tenantId Tenant UUID
   * @param reason Suspension reason
   * @return Updated tenant
   */
  @Transactional
  public TenantDto suspend(UUID tenantId, String reason) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    TenantStatus previousStatus = tenant.getStatus();
    tenant.suspend();
    Tenant saved = tenantRepository.save(tenant);

    eventPublisher.publish(
        new TenantStatusChangedEvent(
            saved.getId(), saved.getUid(), previousStatus, saved.getStatus(), reason));

    log.info("Tenant suspended: id={}, reason={}", tenantId, reason);
    return TenantDto.from(saved);
  }

  /**
   * Cancel tenant subscription permanently.
   *
   * @param tenantId Tenant UUID
   * @param reason Cancellation reason
   * @return Updated tenant
   */
  @Transactional
  public TenantDto cancel(UUID tenantId, String reason) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

    TenantStatus previousStatus = tenant.getStatus();
    tenant.cancel();
    Tenant saved = tenantRepository.save(tenant);

    eventPublisher.publish(
        new TenantStatusChangedEvent(
            saved.getId(), saved.getUid(), previousStatus, saved.getStatus(), reason));

    log.info("Tenant cancelled: id={}, reason={}", tenantId, reason);
    return TenantDto.from(saved);
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
      if (!tenantRepository.existsByUid(candidateUid)) {
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

    if (!tenantRepository.existsBySlug(baseSlug)) {
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
    } while (tenantRepository.existsBySlug(candidateSlug));

    return candidateSlug;
  }

  /**
   * Check if tenant exists.
   *
   * @param tenantId Tenant UUID
   * @return true if tenant exists
   */
  public boolean exists(UUID tenantId) {
    return tenantRepository.existsById(tenantId);
  }

  /**
   * Get tenant entity by ID (for internal use).
   *
   * @param tenantId Tenant UUID
   * @return Tenant entity if found
   */
  @Transactional(readOnly = true)
  public Optional<Tenant> getTenantEntity(UUID tenantId) {
    return tenantRepository.findById(tenantId);
  }
}
