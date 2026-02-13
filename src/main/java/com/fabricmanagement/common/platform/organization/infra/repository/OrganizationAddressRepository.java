package com.fabricmanagement.common.platform.organization.infra.repository;

import com.fabricmanagement.common.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.common.platform.organization.domain.OrganizationAddressId;
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

  Optional<OrganizationAddress> findByOrganizationIdAndAddressId(
      UUID organizationId, UUID addressId);

  List<OrganizationAddress> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa WHERE oa.organizationId = :orgId AND oa.isPrimary = true")
  Optional<OrganizationAddress> findPrimaryByOrganizationId(@Param("orgId") UUID organizationId);

  @Query(
      "SELECT oa FROM OrganizationAddress oa WHERE oa.organizationId = :orgId AND oa.isHeadquarters = true")
  Optional<OrganizationAddress> findHeadquartersByOrganizationId(
      @Param("orgId") UUID organizationId);
}
