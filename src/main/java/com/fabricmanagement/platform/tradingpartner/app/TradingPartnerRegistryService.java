package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.domain.VerifiedStatus;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerLinkedEvent;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerRegistryCreatedEvent;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerRegistryDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for platform-level TradingPartnerRegistry operations.
 *
 * <p><b>Note:</b> No tenant context required - operates at platform level.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Registry creation and deduplication via tax_id + country
 *   <li>Linking registries to platform tenants
 *   <li>Verification status management
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerRegistryService {

  private final TradingPartnerRegistryRepository registryRepository;
  private final TradingPartnerRepository partnerRepository;
  private final DomainEventPublisher eventPublisher;

  private static final String DEFAULT_COUNTRY = "TUR";

  /**
   * Find or create registry by tax_id + country.
   *
   * <p>Deduplication: If registry exists with same tax_id + country, returns existing. Otherwise
   * creates new.
   *
   * @param taxId Tax identification number (nullable for foreign partners)
   * @param officialName Official company name
   * @param country ISO 3166-1 alpha-3 country code (defaults to TUR)
   * @return Registry (existing or newly created)
   */
  /**
   * Find or create registry by tax_id + country.
   *
   * <p>Deduplication: If registry exists with same tax_id + country, returns existing. Otherwise
   * creates new.
   *
   * <p><b>Concurrency Safety:</b> Handles race conditions where two concurrent requests try to
   * create the same registry. If a DataIntegrityViolationException occurs (unique constraint
   * violation on tax_id + country), the method retries the lookup instead of failing.
   *
   * @param taxId Tax identification number (nullable for foreign partners)
   * @param officialName Official company name
   * @param country ISO 3166-1 alpha-3 country code (defaults to TUR)
   * @return Registry (existing or newly created)
   */
  @Transactional
  public TradingPartnerRegistry findOrCreate(String taxId, String officialName, String country) {
    String normalizedCountry = country != null ? country.toUpperCase() : DEFAULT_COUNTRY;
    String normalizedTaxId = taxId != null ? taxId.trim() : null;

    // If tax_id is provided, look for existing registry
    if (normalizedTaxId != null && !normalizedTaxId.isEmpty()) {
      Optional<TradingPartnerRegistry> existing =
          registryRepository.findByTaxIdAndCountry(normalizedTaxId, normalizedCountry);

      if (existing.isPresent()) {
        log.debug("Registry found for tax_id={}, country={}", normalizedTaxId, normalizedCountry);
        return existing.get();
      }
    }

    // Create new registry — handle race condition via catch-and-retry
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(normalizedTaxId, officialName, normalizedCountry);
    registry.setUid(generateRegistryUid());

    try {
      TradingPartnerRegistry saved = registryRepository.saveAndFlush(registry);

      // Publish event
      eventPublisher.publish(
          new TradingPartnerRegistryCreatedEvent(
              saved.getId(), saved.getTaxId(), saved.getOfficialName(), saved.getCountry()));

      log.info(
          "Registry created: uid={}, taxId={}, name={}",
          saved.getUid(),
          saved.getTaxId(),
          saved.getOfficialName());

      return saved;
    } catch (DataIntegrityViolationException e) {
      // Race condition: another transaction inserted the same tax_id + country concurrently.
      // Retry the lookup — the record must now exist.
      if (normalizedTaxId != null && !normalizedTaxId.isEmpty()) {
        log.warn(
            "Registry creation race condition detected for tax_id={}, country={}. Retrying lookup.",
            normalizedTaxId,
            normalizedCountry);
        return registryRepository
            .findByTaxIdAndCountry(normalizedTaxId, normalizedCountry)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Registry not found after concurrent insert for tax_id="
                            + normalizedTaxId
                            + ", country="
                            + normalizedCountry,
                        e));
      }
      // No tax_id — this is a real integrity violation, rethrow
      throw e;
    }
  }

  /**
   * Find registry by ID.
   *
   * @param registryId Registry ID
   * @return Registry DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<TradingPartnerRegistryDto> findById(UUID registryId) {
    return registryRepository.findById(registryId).map(TradingPartnerRegistryDto::from);
  }

  /**
   * Find registry by tax_id and country.
   *
   * @param taxId Tax identification number
   * @param country ISO 3166-1 alpha-3 country code
   * @return Registry DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<TradingPartnerRegistryDto> findByTaxIdAndCountry(String taxId, String country) {
    return registryRepository
        .findByTaxIdAndCountry(taxId, country)
        .map(TradingPartnerRegistryDto::from);
  }

  /**
   * Link registry to a platform tenant.
   *
   * <p>Called when a partner company becomes a platform tenant. All tenants with this partner get
   * notified via TradingPartnerLinkedEvent.
   *
   * @param registryId Registry ID
   * @param tenantId Tenant ID to link
   * @param verifierUserId User performing the linking
   */
  @Transactional
  public void linkToTenant(UUID registryId, UUID tenantId, UUID verifierUserId) {
    TradingPartnerRegistry registry =
        registryRepository
            .findById(registryId)
            .orElseThrow(() -> new IllegalArgumentException("Registry not found: " + registryId));

    if (registry.isLinkedToTenant()) {
      if (registry.getLinkedTenantId().equals(tenantId)) {
        log.warn("Registry {} already linked to tenant {}", registryId, tenantId);
        return;
      }
      throw new IllegalStateException(
          "Registry already linked to different tenant: " + registry.getLinkedTenantId());
    }

    registry.linkToTenant(tenantId, verifierUserId);
    registryRepository.save(registry);

    // Find all tenants using this registry
    List<UUID> affectedTenantIds =
        partnerRepository.findByRegistryId(registryId).stream()
            .map(tp -> tp.getTenantId())
            .distinct()
            .toList();

    // Publish event - affected tenants get notified
    eventPublisher.publish(new TradingPartnerLinkedEvent(registryId, tenantId, affectedTenantIds));

    log.info(
        "Registry {} linked to tenant {}, notifying {} tenants",
        registryId,
        tenantId,
        affectedTenantIds.size());
  }

  /**
   * Search registries by name.
   *
   * @param searchTerm Search term (partial match)
   * @return List of matching registries
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerRegistryDto> searchByName(String searchTerm) {
    return registryRepository.searchByName(searchTerm).stream()
        .map(TradingPartnerRegistryDto::from)
        .toList();
  }

  /**
   * Find all unverified registries.
   *
   * @return List of unverified registries
   */
  @Transactional(readOnly = true)
  public List<TradingPartnerRegistryDto> findUnverified() {
    return registryRepository
        .findByVerifiedStatusAndIsActiveTrue(VerifiedStatus.UNVERIFIED)
        .stream()
        .map(TradingPartnerRegistryDto::from)
        .toList();
  }

  /** Generate unique registry UID using UUID suffix for collision safety. */
  private String generateRegistryUid() {
    String uuidSuffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    return "REG-" + uuidSuffix;
  }
}
