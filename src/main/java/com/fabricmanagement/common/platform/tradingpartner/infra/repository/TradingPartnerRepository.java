package com.fabricmanagement.common.platform.tradingpartner.infra.repository;

import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for tenant-level TradingPartner.
 *
 * <p>All queries are tenant-scoped for data isolation.
 */
public interface TradingPartnerRepository extends JpaRepository<TradingPartner, UUID> {

  /** Find by tenant and ID. */
  Optional<TradingPartner> findByTenantIdAndId(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  /** Find by tenant and UID. */
  Optional<TradingPartner> findByTenantIdAndUid(
      @Param("tenantId") UUID tenantId, @Param("uid") String uid);

  /**
   * Find by tenant and registry ID.
   *
   * <p>Used for deduplication check - each tenant can have one TradingPartner per registry.
   *
   * @param tenantId Tenant ID
   * @param registryId Registry ID
   * @return trading partner if exists
   */
  Optional<TradingPartner> findByTenantIdAndRegistryId(
      @Param("tenantId") UUID tenantId, @Param("registryId") UUID registryId);

  /**
   * Find by tenant and legacy Company ID (migration support).
   *
   * <p>Enables dual-read queries during transition period.
   *
   * @param tenantId Tenant ID
   * @param legacyCompanyId Legacy Company.id
   * @return trading partner if found by legacy ID
   */
  Optional<TradingPartner> findByTenantIdAndLegacyCompanyId(
      @Param("tenantId") UUID tenantId, @Param("legacyCompanyId") UUID legacyCompanyId);

  /** Find all active partners for tenant. */
  List<TradingPartner> findByTenantIdAndIsActiveTrue(@Param("tenantId") UUID tenantId);

  /** Find by tenant and partner type. */
  List<TradingPartner> findByTenantIdAndPartnerType(
      @Param("tenantId") UUID tenantId, @Param("partnerType") PartnerType partnerType);

  /** Find by tenant and multiple partner types. */
  List<TradingPartner> findByTenantIdAndPartnerTypeIn(
      @Param("tenantId") UUID tenantId, @Param("types") List<PartnerType> types);

  /** Find by tenant and status. */
  List<TradingPartner> findByTenantIdAndStatus(
      @Param("tenantId") UUID tenantId, @Param("status") PartnerStatus status);

  /** Check if tenant already has this registry. */
  boolean existsByTenantIdAndRegistryId(
      @Param("tenantId") UUID tenantId, @Param("registryId") UUID registryId);

  /** Check if partner exists by ID. */
  boolean existsByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  /**
   * Find all partners that use a specific registry (cross-tenant).
   *
   * <p>Used for event notification when registry is linked to a tenant.
   *
   * @param registryId Registry ID
   * @return all trading partners referencing this registry
   */
  @Query(
      "SELECT tp FROM TradingPartner tp JOIN FETCH tp.registry "
          + "WHERE tp.registry.id = :registryId AND tp.isActive = true")
  List<TradingPartner> findByRegistryId(@Param("registryId") UUID registryId);

  /**
   * Find suppliers (SUPPLIER or BOTH) with registry eagerly loaded.
   *
   * @param tenantId Tenant ID
   * @return list of suppliers
   */
  @Query(
      "SELECT tp FROM TradingPartner tp JOIN FETCH tp.registry "
          + "WHERE tp.tenantId = :tenantId "
          + "AND tp.isActive = true "
          + "AND tp.partnerType IN ('SUPPLIER', 'BOTH') "
          + "ORDER BY tp.customName, tp.registry.officialName")
  List<TradingPartner> findSuppliers(@Param("tenantId") UUID tenantId);

  /**
   * Find customers (CUSTOMER or BOTH) with registry eagerly loaded.
   *
   * @param tenantId Tenant ID
   * @return list of customers
   */
  @Query(
      "SELECT tp FROM TradingPartner tp JOIN FETCH tp.registry "
          + "WHERE tp.tenantId = :tenantId "
          + "AND tp.isActive = true "
          + "AND tp.partnerType IN ('CUSTOMER', 'BOTH') "
          + "ORDER BY tp.customName, tp.registry.officialName")
  List<TradingPartner> findCustomers(@Param("tenantId") UUID tenantId);

  /**
   * Find fason partners with registry eagerly loaded.
   *
   * @param tenantId Tenant ID
   * @return list of fason partners
   */
  @Query(
      "SELECT tp FROM TradingPartner tp JOIN FETCH tp.registry "
          + "WHERE tp.tenantId = :tenantId "
          + "AND tp.isActive = true "
          + "AND tp.partnerType = 'FASON' "
          + "ORDER BY tp.customName, tp.registry.officialName")
  List<TradingPartner> findFasonPartners(@Param("tenantId") UUID tenantId);

  /**
   * Search partners by name (custom name or official name) with registry eagerly loaded.
   *
   * @param tenantId Tenant ID
   * @param searchTerm Search term
   * @return matching partners
   */
  @Query(
      "SELECT tp FROM TradingPartner tp JOIN FETCH tp.registry "
          + "WHERE tp.tenantId = :tenantId "
          + "AND tp.isActive = true "
          + "AND (LOWER(tp.customName) LIKE LOWER(CONCAT('%', :term, '%')) "
          + "     OR LOWER(tp.registry.officialName) LIKE LOWER(CONCAT('%', :term, '%'))) "
          + "ORDER BY tp.customName, tp.registry.officialName")
  List<TradingPartner> searchByName(
      @Param("tenantId") UUID tenantId, @Param("term") String searchTerm);

  /**
   * Count partners by type for tenant.
   *
   * @param tenantId Tenant ID
   * @param partnerType Partner type
   * @return count
   */
  long countByTenantIdAndPartnerTypeAndIsActiveTrue(
      @Param("tenantId") UUID tenantId, @Param("partnerType") PartnerType partnerType);
}
