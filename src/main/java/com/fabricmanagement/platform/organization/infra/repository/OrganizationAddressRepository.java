package com.fabricmanagement.platform.organization.infra.repository;

import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.domain.OrganizationAddressId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for OrganizationAddress junction entity. */
@Repository
public interface OrganizationAddressRepository
    extends JpaRepository<OrganizationAddress, OrganizationAddressId> {

  /** Finds the assignment regardless of soft-delete status. Used by cascade operations. */
  Optional<OrganizationAddress> findByOrganizationIdAndAddressId(
      UUID organizationId, UUID addressId);

  /** Finds only active assignments. Used by business-logic operations. */
  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "WHERE oa.organizationId = :orgId AND oa.addressId = :addressId "
          + "AND oa.isActive = true")
  Optional<OrganizationAddress> findActiveByOrganizationIdAndAddressId(
      @Param("orgId") UUID organizationId, @Param("addressId") UUID addressId);

  /** Finds only active assignments with the address eagerly fetched. */
  @Query(
      "SELECT oa FROM OrganizationAddress oa LEFT JOIN FETCH oa.address "
          + "WHERE oa.organizationId = :orgId AND oa.addressId = :addressId "
          + "AND oa.isActive = true")
  Optional<OrganizationAddress> findActiveWithAddressByOrganizationIdAndAddressId(
      @Param("orgId") UUID organizationId, @Param("addressId") UUID addressId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "WHERE oa.tenantId = :tenantId AND oa.organizationId = :orgId "
          + "AND oa.isActive = true")
  List<OrganizationAddress> findByTenantIdAndOrganizationId(
      @Param("tenantId") UUID tenantId, @Param("orgId") UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa LEFT JOIN FETCH oa.address"
          + " WHERE oa.tenantId = :tenantId AND oa.organizationId = :orgId"
          + " AND oa.isActive = true")
  List<OrganizationAddress> findWithAddressByTenantIdAndOrganizationId(
      @Param("tenantId") UUID tenantId, @Param("orgId") UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "WHERE oa.organizationId = :orgId AND oa.isPrimary = true AND oa.isActive = true")
  Optional<OrganizationAddress> findPrimaryByOrganizationId(@Param("orgId") UUID organizationId);

  /** Primary assignment with address eagerly fetched (avoids lazy load outside transaction). */
  @Query(
      "SELECT oa FROM OrganizationAddress oa LEFT JOIN FETCH oa.address "
          + "WHERE oa.organizationId = :orgId AND oa.isPrimary = true AND oa.isActive = true")
  Optional<OrganizationAddress> findPrimaryWithAddressByOrganizationId(
      @Param("orgId") UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "WHERE oa.organizationId = :orgId AND oa.isHeadquarters = true AND oa.isActive = true")
  Optional<OrganizationAddress> findHeadquartersByOrganizationId(
      @Param("orgId") UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "LEFT JOIN FETCH oa.organization "
          + "WHERE oa.addressId = :addressId AND oa.isActive = true")
  Optional<OrganizationAddress> findByAddressId(@Param("addressId") UUID addressId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "WHERE oa.addressId = :addressId")
  Optional<OrganizationAddress> findByAddressIdIncludingDeleted(@Param("addressId") UUID addressId);
}
