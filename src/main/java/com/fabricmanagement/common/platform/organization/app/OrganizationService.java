package com.fabricmanagement.common.platform.organization.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.common.platform.organization.domain.Organization;
import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
import com.fabricmanagement.common.platform.organization.domain.event.OrganizationCreatedEvent;
import com.fabricmanagement.common.platform.organization.dto.CreateOrganizationRequest;
import com.fabricmanagement.common.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.common.platform.user.dto.CompleteOnboardingRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Organization management.
 *
 * <p>Handles CRUD operations for internal organizational structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final DomainEventPublisher eventPublisher;

  // ========================================
  // CREATE OPERATIONS
  // ========================================

  /**
   * Create a new organization within the current tenant.
   *
   * @param request Creation request
   * @return Created organization DTO
   */
  @Transactional
  public OrganizationDto createOrganization(CreateOrganizationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context must be set to create an organization");
    }

    // Validate tax ID uniqueness within tenant
    if (organizationRepository.existsByTenantIdAndTaxId(tenantId, request.getTaxId())) {
      throw new TaxIdAlreadyExistsException(
          "Organization with this tax ID already exists in your tenant");
    }

    // Validate parent if provided
    if (request.getParentOrganizationId() != null) {
      if (!organizationRepository.existsByTenantIdAndId(
          tenantId, request.getParentOrganizationId())) {
        throw new IllegalArgumentException("Parent organization not found");
      }
    }

    Organization organization =
        Organization.create(request.getName(), request.getTaxId(), request.getOrganizationType());
    organization.setParent(request.getParentOrganizationId());

    Organization saved = organizationRepository.save(organization);

    // Publish event
    eventPublisher.publish(
        new OrganizationCreatedEvent(
            saved.getTenantId(), saved.getId(), saved.getName(), saved.getOrganizationType()));

    log.info(
        "Organization created: id={}, uid={}, tenantId={}",
        saved.getId(),
        saved.getUid(),
        saved.getTenantId());
    return OrganizationDto.from(saved);
  }

  /**
   * Create root organization for a new tenant (during onboarding).
   *
   * <p>This is called after Tenant is created. The organization will be the root (no parent).
   *
   * @param tenantId Tenant UUID
   * @param name Organization name
   * @param taxId Tax ID
   * @param organizationType Organization type
   * @return Created organization DTO
   */
  @Transactional
  public OrganizationDto createRootOrganization(
      UUID tenantId, String name, String taxId, OrganizationType organizationType) {
    log.debug("Creating root organization for tenant: {}", tenantId);

    // Validate tax ID global uniqueness for root organizations
    if (organizationRepository.existsByTaxId(taxId)) {
      throw new TaxIdAlreadyExistsException("Organization with this tax ID already exists");
    }

    // Set tenant context for entity creation
    UUID originalTenantId = TenantContext.getCurrentTenantIdOrNull();
    try {
      TenantContext.setCurrentTenantId(tenantId);

      Organization organization = Organization.create(name, taxId, organizationType);
      Organization saved = organizationRepository.save(organization);

      eventPublisher.publish(
          new OrganizationCreatedEvent(
              saved.getTenantId(), saved.getId(), saved.getName(), saved.getOrganizationType()));

      log.info(
          "Root organization created: id={}, uid={}, tenantId={}",
          saved.getId(),
          saved.getUid(),
          saved.getTenantId());
      return OrganizationDto.from(saved);
    } finally {
      if (originalTenantId != null) {
        TenantContext.setCurrentTenantId(originalTenantId);
      }
    }
  }

  // ========================================
  // READ OPERATIONS
  // ========================================

  /**
   * Find organization by ID within current tenant.
   *
   * @param id Organization UUID
   * @return Organization if found
   */
  @Transactional(readOnly = true)
  public Optional<OrganizationDto> findById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationRepository.findByTenantIdAndId(tenantId, id).map(OrganizationDto::from);
  }

  /**
   * Find organization by ID within specified tenant.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return Organization if found
   */
  @Transactional(readOnly = true)
  public Optional<OrganizationDto> findById(UUID tenantId, UUID id) {
    return organizationRepository.findByTenantIdAndId(tenantId, id).map(OrganizationDto::from);
  }

  /**
   * Get all active organizations for current tenant.
   *
   * @return List of organizations
   */
  @Transactional(readOnly = true)
  public List<OrganizationDto> getAllActive() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(OrganizationDto::from)
        .toList();
  }

  /**
   * Get organizations by type.
   *
   * @param organizationType Organization type
   * @return List of organizations
   */
  @Transactional(readOnly = true)
  public List<OrganizationDto> getByType(OrganizationType organizationType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationRepository
        .findByTenantIdAndOrganizationType(tenantId, organizationType)
        .stream()
        .map(OrganizationDto::from)
        .toList();
  }

  /**
   * Get child organizations.
   *
   * @param parentOrganizationId Parent organization UUID
   * @return List of child organizations
   */
  @Transactional(readOnly = true)
  public List<OrganizationDto> getChildren(UUID parentOrganizationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationRepository
        .findByTenantIdAndParentOrganizationId(tenantId, parentOrganizationId)
        .stream()
        .map(OrganizationDto::from)
        .toList();
  }

  /**
   * Get root organization for current tenant.
   *
   * @return Root organization if found
   */
  @Transactional(readOnly = true)
  public Optional<OrganizationDto> getRootOrganization() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return organizationRepository
        .findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER)
        .map(OrganizationDto::from);
  }

  // ========================================
  // UPDATE OPERATIONS
  // ========================================

  /**
   * Enrich the tenant's root organization with onboarding profile data.
   *
   * <p>Applies only non-null fields from the request so that partial payloads are safe. Skips
   * silently when the root organization cannot be resolved (edge case: no root yet).
   *
   * @param tenantId Tenant UUID
   * @param request Onboarding enrichment data
   */
  @Transactional
  public void enrichRootOrganization(UUID tenantId, CompleteOnboardingRequest request) {
    if (request == null) return;

    Organization root =
        organizationRepository
            .findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER)
            .orElse(null);

    if (root == null) {
      log.warn("enrichRootOrganization: root organization not found for tenant={}", tenantId);
      return;
    }

    if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
      root.setName(request.getCompanyName().trim());
    }
    if (request.getTaxId() != null && !request.getTaxId().isBlank()) {
      String newTaxId = request.getTaxId().trim();
      if (!newTaxId.equals(root.getTaxId())
          && !organizationRepository.existsByTenantIdAndTaxId(tenantId, newTaxId)) {
        root.setTaxId(newTaxId);
      }
    }

    root.enrich(
        request.getLegalName() != null ? request.getLegalName().trim() : null,
        request.getRegistrationNumber() != null ? request.getRegistrationNumber().trim() : null,
        request.getIndustry() != null ? request.getIndustry().trim() : null,
        request.getWebsite() != null ? request.getWebsite().trim() : null,
        request.getDescription() != null ? request.getDescription().trim() : null);

    organizationRepository.save(root);
    log.info("enrichRootOrganization: enriched orgId={}, tenantId={}", root.getId(), tenantId);
  }

  /**
   * Update organization basic info.
   *
   * @param id Organization UUID
   * @param name New name
   * @param taxId New tax ID
   * @return Updated organization
   */
  @Transactional
  public OrganizationDto updateOrganization(UUID id, String name, String taxId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Organization organization =
        organizationRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

    // Validate tax ID uniqueness if changed
    if (!organization.getTaxId().equals(taxId)
        && organizationRepository.existsByTenantIdAndTaxId(tenantId, taxId)) {
      throw new TaxIdAlreadyExistsException("Organization with this tax ID already exists");
    }

    organization.update(name, taxId);
    Organization saved = organizationRepository.save(organization);

    log.info("Organization updated: id={}", saved.getId());
    return OrganizationDto.from(saved);
  }

  /**
   * Deactivate organization (soft delete).
   *
   * @param id Organization UUID
   */
  @Transactional
  public void deactivateOrganization(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Organization organization =
        organizationRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

    organization.delete();
    organizationRepository.save(organization);

    log.info("Organization deactivated: id={}", id);
  }

  // ========================================
  // PARTNER ORGANIZATION
  // ========================================

  /**
   * Create a partner organization for a trading partner.
   *
   * <p>Auto-created when a TradingPartner is registered. This organization serves as the anchor for
   * external users associated with this partner. Uses {@link OrganizationType#EXTERNAL_PARTNER} to
   * distinguish from internal organizations.
   *
   * <p><b>Tax ID handling:</b> If the partner has no tax_id (foreign/unregistered), a generated
   * placeholder is used (TP-{partnerUid}).
   *
   * @param name Partner display name
   * @param taxId Partner tax ID (nullable — placeholder will be generated)
   * @param partnerUid Partner UID for placeholder generation
   * @return Created organization DTO
   */
  @Transactional
  public OrganizationDto createPartnerOrganization(String name, String taxId, String partnerUid) {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      throw new IllegalStateException(
          "Tenant context must be set to create a partner organization");
    }

    // Use placeholder tax ID for partners without one (foreign/unregistered)
    String effectiveTaxId = (taxId != null && !taxId.isBlank()) ? taxId : "TP-" + partnerUid;

    // Check if an organization with this tax ID already exists within the tenant
    Optional<Organization> existing =
        organizationRepository.findByTenantIdAndTaxId(tenantId, effectiveTaxId);
    if (existing.isPresent()) {
      log.debug(
          "Partner organization already exists for taxId={}, returning existing: id={}",
          effectiveTaxId,
          existing.get().getId());
      return OrganizationDto.from(existing.get());
    }

    Organization organization =
        Organization.create(name, effectiveTaxId, OrganizationType.EXTERNAL_PARTNER);

    Organization saved = organizationRepository.save(organization);

    eventPublisher.publish(
        new OrganizationCreatedEvent(
            saved.getTenantId(), saved.getId(), saved.getName(), saved.getOrganizationType()));

    log.info(
        "Partner organization created: id={}, uid={}, name={}, tenantId={}",
        saved.getId(),
        saved.getUid(),
        saved.getName(),
        saved.getTenantId());

    return OrganizationDto.from(saved);
  }

  // ========================================
  // UTILITY
  // ========================================

  /**
   * Check if organization exists.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return true if exists
   */
  public boolean exists(UUID tenantId, UUID id) {
    return organizationRepository.existsByTenantIdAndId(tenantId, id);
  }

  /**
   * Get all active organizations for specified tenant.
   *
   * @param tenantId Tenant UUID
   * @return List of organizations
   */
  @Transactional(readOnly = true)
  public List<OrganizationDto> findByTenant(UUID tenantId) {
    return organizationRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(OrganizationDto::from)
        .toList();
  }

  /**
   * Check if any organization has the given tax ID (global uniqueness).
   *
   * @param taxId tax ID
   * @return true if tax ID already exists
   */
  public boolean existsByTaxId(String taxId) {
    return organizationRepository.existsByTaxId(taxId);
  }
}
