package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.PartnerType;
import com.fabricmanagement.common.platform.company.domain.TradingPartner;
import com.fabricmanagement.common.platform.company.domain.TradingPartnerRegistry;
import com.fabricmanagement.common.platform.company.domain.event.TradingPartnerCreatedEvent;
import com.fabricmanagement.common.platform.company.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.common.platform.company.dto.TradingPartnerDto;
import com.fabricmanagement.common.platform.company.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing trading partners.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Partner creation with automatic registry deduplication
 *   <li>Partner queries with dual-read support (legacy_company_id lookup)
 *   <li>Partner lifecycle (suspend, block, reactivate)
 * </ul>
 *
 * <h2>Dual-Read Support (Faz 3):</h2>
 *
 * <p>After migration, queries check:
 *
 * <ol>
 *   <li>TradingPartner by ID
 *   <li>TradingPartner by legacy_company_id (for old references)
 * </ol>
 *
 * <p><b>Note:</b> Legacy Company fallback has been removed after Faz 3 migration. All partner data
 * is now in TradingPartner table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerService {

  private final TradingPartnerRepository partnerRepository;
  private final TradingPartnerRegistryService registryService;
  private final DomainEventPublisher eventPublisher;

  private static final String DEFAULT_COUNTRY = "TUR";

  // ═══════════════════════════════════════════════════════════════════════════
  // CREATION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a new trading partner relationship.
   *
   * <p>Automatically handles registry deduplication: if a registry with same tax_id + country
   * exists, links to it. If same registry already exists for this tenant, upgrades to BOTH.
   *
   * @param request Partner creation request
   * @return Created partner DTO
   */
  @Transactional
  public TradingPartnerDto createPartner(CreateTradingPartnerRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    String country =
        request.getCountry() != null ? request.getCountry().toUpperCase() : DEFAULT_COUNTRY;

    // Find or create registry (platform-level deduplication)
    TradingPartnerRegistry registry =
        registryService.findOrCreate(request.getTaxId(), request.getCompanyName(), country);

    // Check if this tenant already has this registry
    Optional<TradingPartner> existing =
        partnerRepository.findByTenantIdAndRegistryId(tenantId, registry.getId());

    if (existing.isPresent()) {
      TradingPartner partner = existing.get();
      // Upgrade to BOTH if adding different type
      if (partner.getPartnerType() != request.getPartnerType()
          && partner.getPartnerType() != PartnerType.BOTH) {
        partner.upgradeToMultiType(request.getPartnerType());
        TradingPartner updated = partnerRepository.save(partner);
        log.info(
            "Partner upgraded to BOTH: uid={}, registry={}", updated.getUid(), registry.getUid());
        return TradingPartnerDto.from(updated);
      }
      // Already exists with same or BOTH type
      log.debug(
          "Partner already exists: uid={}, type={}", partner.getUid(), partner.getPartnerType());
      return TradingPartnerDto.from(partner);
    }

    // Create new TradingPartner
    TradingPartner partner =
        TradingPartner.create(registry, request.getPartnerType(), request.getCustomName());

    if (request.getRelationshipMeta() != null) {
      partner.setRelationshipMeta(request.getRelationshipMeta());
    }

    TradingPartner saved = partnerRepository.save(partner);

    // Publish event
    eventPublisher.publish(
        new TradingPartnerCreatedEvent(
            tenantId,
            saved.getId(),
            registry.getId(),
            saved.getPartnerType().name(),
            saved.getDisplayName(),
            null // No legacy ID for new partners
            ));

    log.info(
        "Trading partner created: uid={}, registry={}, type={}",
        saved.getUid(),
        registry.getUid(),
        saved.getPartnerType());

    return TradingPartnerDto.from(saved);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES (with dual-read support)
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find partner by ID (dual-read).
   *
   * <p>Checks:
   *
   * <ol>
   *   <li>TradingPartner by ID
   *   <li>TradingPartner by legacy_company_id (for backward compatibility)
   * </ol>
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID (or legacy Company ID)
   * @return Partner DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<TradingPartnerDto> findById(UUID tenantId, UUID partnerId) {
    // 1. Check TradingPartner by ID
    Optional<TradingPartner> tp = partnerRepository.findByTenantIdAndId(tenantId, partnerId);
    if (tp.isPresent()) {
      return tp.map(TradingPartnerDto::from);
    }

    // 2. Check TradingPartner by legacy_company_id (backward compatibility)
    tp = partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId);
    if (tp.isPresent()) {
      log.debug("Found partner via legacy_company_id: {}", partnerId);
      return tp.map(TradingPartnerDto::from);
    }

    return Optional.empty();
  }

  /**
   * Find all active partners for tenant.
   *
   * @param tenantId Tenant ID
   * @return List of partners
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> findAll(UUID tenantId) {
    return partnerRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(TradingPartnerDto::from)
        .toList();
  }

  /**
   * Find suppliers (SUPPLIER or BOTH).
   *
   * @param tenantId Tenant ID
   * @return List of suppliers
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> findSuppliers(UUID tenantId) {
    return partnerRepository.findSuppliers(tenantId).stream().map(TradingPartnerDto::from).toList();
  }

  /**
   * Find customers (CUSTOMER or BOTH).
   *
   * @param tenantId Tenant ID
   * @return List of customers
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> findCustomers(UUID tenantId) {
    return partnerRepository.findCustomers(tenantId).stream().map(TradingPartnerDto::from).toList();
  }

  /**
   * Find fason partners.
   *
   * @param tenantId Tenant ID
   * @return List of fason partners
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> findFasonPartners(UUID tenantId) {
    return partnerRepository.findFasonPartners(tenantId).stream()
        .map(TradingPartnerDto::from)
        .toList();
  }

  /**
   * Find by partner type.
   *
   * @param tenantId Tenant ID
   * @param type Partner type
   * @return List of partners
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> findByType(UUID tenantId, PartnerType type) {
    // For SUPPLIER/CUSTOMER, also include BOTH
    List<PartnerType> types =
        switch (type) {
          case SUPPLIER -> List.of(PartnerType.SUPPLIER, PartnerType.BOTH);
          case CUSTOMER -> List.of(PartnerType.CUSTOMER, PartnerType.BOTH);
          default -> List.of(type);
        };

    return partnerRepository.findByTenantIdAndPartnerTypeIn(tenantId, types).stream()
        .map(TradingPartnerDto::from)
        .toList();
  }

  /**
   * Search partners by name.
   *
   * @param tenantId Tenant ID
   * @param searchTerm Search term
   * @return List of matching partners
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerDto> searchByName(UUID tenantId, String searchTerm) {
    return partnerRepository.searchByName(tenantId, searchTerm).stream()
        .map(TradingPartnerDto::from)
        .toList();
  }

  /**
   * Check if partner exists.
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID (or legacy Company ID)
   * @return true if exists
   */
  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID partnerId) {
    if (partnerRepository.findByTenantIdAndId(tenantId, partnerId).isPresent()) {
      return true;
    }
    return partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId).isPresent();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Suspend a partner relationship.
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID
   */
  @Transactional
  public void suspend(UUID tenantId, UUID partnerId) {
    TradingPartner partner = getPartnerOrThrow(tenantId, partnerId);
    partner.suspend();
    partnerRepository.save(partner);
    log.info("Partner suspended: uid={}", partner.getUid());
  }

  /**
   * Block a partner relationship.
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID
   */
  @Transactional
  public void block(UUID tenantId, UUID partnerId) {
    TradingPartner partner = getPartnerOrThrow(tenantId, partnerId);
    partner.block();
    partnerRepository.save(partner);
    log.info("Partner blocked: uid={}", partner.getUid());
  }

  /**
   * Reactivate a suspended partner.
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID
   */
  @Transactional
  public void reactivate(UUID tenantId, UUID partnerId) {
    TradingPartner partner = getPartnerOrThrow(tenantId, partnerId);
    partner.reactivate();
    partnerRepository.save(partner);
    log.info("Partner reactivated: uid={}", partner.getUid());
  }

  /**
   * Soft delete a partner.
   *
   * @param tenantId Tenant ID
   * @param partnerId Partner ID
   */
  @Transactional
  public void delete(UUID tenantId, UUID partnerId) {
    TradingPartner partner = getPartnerOrThrow(tenantId, partnerId);
    partner.delete();
    partnerRepository.save(partner);
    log.info("Partner deleted (soft): uid={}", partner.getUid());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  private TradingPartner getPartnerOrThrow(UUID tenantId, UUID partnerId) {
    return partnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .or(() -> partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId))
        .orElseThrow(() -> new IllegalArgumentException("Trading partner not found: " + partnerId));
  }
}
