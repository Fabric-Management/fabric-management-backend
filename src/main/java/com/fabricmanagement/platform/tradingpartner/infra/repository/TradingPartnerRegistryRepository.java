package com.fabricmanagement.platform.tradingpartner.infra.repository;

import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.domain.VerifiedStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for platform-level TradingPartnerRegistry.
 *
 * <p><b>Note:</b> No tenant filtering - registry is platform-wide for cross-tenant deduplication.
 */
public interface TradingPartnerRegistryRepository
    extends JpaRepository<TradingPartnerRegistry, UUID> {

  /** Find by UID. */
  Optional<TradingPartnerRegistry> findByUid(String uid);

  /**
   * Find by tax ID and country (deduplication lookup).
   *
   * @param taxId Tax identification number
   * @param country ISO 3166-1 alpha-3 country code
   * @return registry if exists with this tax_id + country
   */
  Optional<TradingPartnerRegistry> findByTaxIdAndCountry(String taxId, String country);

  /**
   * Find by tax ID only (for cases where country is not specified).
   *
   * @param taxId Tax identification number
   * @return registry if exists with this tax_id
   */
  Optional<TradingPartnerRegistry> findByTaxId(String taxId);

  /**
   * Check if registry exists for tax ID and country.
   *
   * @param taxId Tax identification number
   * @param country ISO 3166-1 alpha-3 country code
   * @return true if exists
   */
  boolean existsByTaxIdAndCountry(String taxId, String country);

  /**
   * Find all registries linked to a specific tenant.
   *
   * @param linkedTenantId The linked tenant ID
   * @return list of registries linked to this tenant
   */
  List<TradingPartnerRegistry> findByLinkedTenantId(UUID linkedTenantId);

  /**
   * Find by name and country for partners without tax ID.
   *
   * <p>Used for fuzzy matching when tax_id is not available.
   *
   * @param officialName Official company name
   * @param country ISO 3166-1 alpha-3 country code
   * @return matching registries
   */
  @Query(
      "SELECT r FROM TradingPartnerRegistry r "
          + "WHERE r.taxId IS NULL "
          + "AND LOWER(r.officialName) = LOWER(:name) "
          + "AND r.country = :country "
          + "AND r.isActive = true")
  List<TradingPartnerRegistry> findByNameAndCountryWithoutTaxId(
      @Param("name") String officialName, @Param("country") String country);

  /**
   * Find all active registries by verification status.
   *
   * @param status Verification status
   * @return list of matching registries
   */
  List<TradingPartnerRegistry> findByVerifiedStatusAndIsActiveTrue(VerifiedStatus status);

  /**
   * Search registries by name (case-insensitive, partial match).
   *
   * @param name Search term
   * @return matching registries
   */
  @Query(
      "SELECT r FROM TradingPartnerRegistry r "
          + "WHERE LOWER(r.officialName) LIKE LOWER(CONCAT('%', :name, '%')) "
          + "AND r.isActive = true "
          + "ORDER BY r.officialName")
  List<TradingPartnerRegistry> searchByName(@Param("name") String name);
}
