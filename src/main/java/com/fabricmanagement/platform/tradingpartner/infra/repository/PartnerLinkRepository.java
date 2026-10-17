package com.fabricmanagement.platform.tradingpartner.infra.repository;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerLink;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped access to the companies linked to a customer. */
public interface PartnerLinkRepository extends JpaRepository<PartnerLink, UUID> {

  /** Active links of one customer with the linked company and its registry record loaded. */
  @Query(
      "SELECT l FROM PartnerLink l JOIN FETCH l.linkedPartner lp JOIN FETCH lp.registry reg "
          + "WHERE l.tenantId = :tenantId "
          + "AND l.customerPartner.id = :customerId "
          + "AND l.isActive = true "
          + "ORDER BY reg.officialName, l.role")
  List<PartnerLink> findActiveByTenantIdAndCustomerId(
      @Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

  /** One active link of the customer, with both ends loaded. */
  @Query(
      "SELECT l FROM PartnerLink l JOIN FETCH l.customerPartner "
          + "JOIN FETCH l.linkedPartner lp JOIN FETCH lp.registry "
          + "WHERE l.tenantId = :tenantId "
          + "AND l.customerPartner.id = :customerId "
          + "AND l.id = :linkId "
          + "AND l.isActive = true")
  Optional<PartnerLink> findActiveByTenantIdAndCustomerIdAndId(
      @Param("tenantId") UUID tenantId,
      @Param("customerId") UUID customerId,
      @Param("linkId") UUID linkId);

  /** Number of active links for the same customer, linked company and role (0 or 1). */
  @Query(
      "SELECT COUNT(l) FROM PartnerLink l "
          + "WHERE l.tenantId = :tenantId "
          + "AND l.customerPartner.id = :customerId "
          + "AND l.linkedPartner.id = :linkedPartnerId "
          + "AND l.role = :role "
          + "AND l.isActive = true")
  long countActive(
      @Param("tenantId") UUID tenantId,
      @Param("customerId") UUID customerId,
      @Param("linkedPartnerId") UUID linkedPartnerId,
      @Param("role") PartnerLinkRole role);
}
