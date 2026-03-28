package com.fabricmanagement.platform.organization.api.facade;

import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.CreateOrganizationRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Facade for cross-module Organization access.
 *
 * <p>Provides a stable API for other modules to interact with Organization without depending on
 * internal implementation details.
 */
@Component
@RequiredArgsConstructor
public class OrganizationFacade {

  private final OrganizationService organizationService;

  /**
   * Create organization within current tenant.
   *
   * @param request Creation request
   * @return Created organization
   */
  public OrganizationDto createOrganization(CreateOrganizationRequest request) {
    return organizationService.createOrganization(request);
  }

  /**
   * Create root organization for a new tenant (during onboarding).
   *
   * @param tenantId Tenant UUID
   * @param name Organization name
   * @param taxId Tax ID
   * @param organizationType Organization type
   * @return Created organization
   */
  public OrganizationDto createRootOrganization(
      UUID tenantId, String name, String taxId, OrganizationType organizationType) {
    return organizationService.createRootOrganization(tenantId, name, taxId, organizationType);
  }

  /**
   * Find organization by ID within current tenant.
   *
   * @param id Organization UUID
   * @return Organization if found
   */
  public Optional<OrganizationDto> findById(UUID id) {
    return organizationService.findById(id);
  }

  /**
   * Find organization by ID within specified tenant.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return Organization if found
   */
  public Optional<OrganizationDto> findById(UUID tenantId, UUID id) {
    return organizationService.findById(tenantId, id);
  }

  /**
   * Get all active organizations for current tenant.
   *
   * @return List of organizations
   */
  public List<OrganizationDto> getAllActive() {
    return organizationService.getAllActive();
  }

  /**
   * Get root organization for current tenant.
   *
   * @return Root organization if found
   */
  public Optional<OrganizationDto> getRootOrganization() {
    return organizationService.getRootOrganization();
  }

  /**
   * Check if organization exists.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return true if exists
   */
  public boolean exists(UUID tenantId, UUID id) {
    return organizationService.exists(tenantId, id);
  }

  /**
   * Update organization.
   *
   * @param id Organization UUID
   * @param name New name
   * @param taxId New tax ID
   * @param legalName Legal registered name (optional)
   * @return Updated organization
   */
  public OrganizationDto updateOrganization(UUID id, String name, String taxId, String legalName) {
    return organizationService.updateOrganization(id, name, taxId, legalName);
  }

  /**
   * Deactivate organization.
   *
   * @param id Organization UUID
   */
  public void deactivateOrganization(UUID id) {
    organizationService.deactivateOrganization(id);
  }

  // ========================================
  // PARTNER ORGANIZATION
  // ========================================

  /**
   * Create a partner organization for a trading partner.
   *
   * <p>Called by TradingPartnerService when creating a new partner. The organization serves as user
   * anchor for external partner users.
   *
   * @param name Partner display name
   * @param taxId Partner tax ID (nullable)
   * @param partnerUid Partner UID for placeholder generation
   * @return Created organization
   */
  public OrganizationDto createPartnerOrganization(String name, String taxId, String partnerUid) {
    return organizationService.createPartnerOrganization(name, taxId, partnerUid);
  }

  // ========================================
  // ADDITIONAL METHODS
  // ========================================

  /**
   * Get all active organizations for specified tenant.
   *
   * @param tenantId Tenant UUID
   * @return List of organizations
   */
  public List<OrganizationDto> findByTenant(UUID tenantId) {
    return organizationService.findByTenant(tenantId);
  }

  /**
   * Check if any organization has the given tax ID (global uniqueness).
   *
   * @param taxId tax ID
   * @return true if tax ID already exists
   */
  public boolean existsByTaxId(String taxId) {
    return organizationService.existsByTaxId(taxId);
  }
}
