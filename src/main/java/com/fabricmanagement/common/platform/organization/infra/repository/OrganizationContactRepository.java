package com.fabricmanagement.common.platform.organization.infra.repository;

import com.fabricmanagement.common.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.common.platform.organization.domain.OrganizationContactId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for OrganizationContact junction entity. */
@Repository
public interface OrganizationContactRepository
    extends JpaRepository<OrganizationContact, OrganizationContactId> {

  Optional<OrganizationContact> findByOrganizationIdAndContactId(
      UUID organizationId, UUID contactId);

  List<OrganizationContact> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

  @Query(
      "SELECT oc FROM OrganizationContact oc LEFT JOIN FETCH oc.contact"
          + " WHERE oc.tenantId = :tenantId AND oc.organizationId = :orgId")
  List<OrganizationContact> findWithContactByTenantIdAndOrganizationId(
      @Param("tenantId") UUID tenantId, @Param("orgId") UUID organizationId);

  @Query(
      "SELECT oc FROM OrganizationContact oc WHERE oc.organizationId = :orgId AND oc.isDefault = true")
  Optional<OrganizationContact> findDefaultByOrganizationId(@Param("orgId") UUID organizationId);

  List<OrganizationContact> findByOrganizationIdAndDepartment(
      UUID organizationId, String department);
}
