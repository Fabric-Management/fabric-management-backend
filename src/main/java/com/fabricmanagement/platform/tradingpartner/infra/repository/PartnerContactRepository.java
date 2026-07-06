package com.fabricmanagement.platform.tradingpartner.infra.repository;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerContactRepository extends JpaRepository<PartnerContact, UUID> {

  @Query(
      "SELECT pc FROM PartnerContact pc JOIN FETCH pc.partner p "
          + "WHERE pc.tenantId = :tenantId "
          + "AND p.id = :partnerId "
          + "AND pc.isActive = true "
          + "ORDER BY pc.role, pc.primaryContact DESC, pc.name")
  List<PartnerContact> findActiveByTenantIdAndPartnerId(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  @Query(
      "SELECT pc FROM PartnerContact pc JOIN FETCH pc.partner p "
          + "WHERE pc.tenantId = :tenantId "
          + "AND p.id = :partnerId "
          + "AND pc.role = :role "
          + "AND pc.isActive = true "
          + "ORDER BY pc.primaryContact DESC, pc.name")
  List<PartnerContact> findActiveByTenantIdAndPartnerIdAndRole(
      @Param("tenantId") UUID tenantId,
      @Param("partnerId") UUID partnerId,
      @Param("role") PartnerContactRole role);

  @Query(
      "SELECT pc FROM PartnerContact pc JOIN FETCH pc.partner p "
          + "WHERE pc.tenantId = :tenantId "
          + "AND pc.id = :contactId "
          + "AND pc.isActive = true")
  Optional<PartnerContact> findActiveByTenantIdAndId(
      @Param("tenantId") UUID tenantId, @Param("contactId") UUID contactId);

  @Query(
      "SELECT pc FROM PartnerContact pc "
          + "WHERE pc.tenantId = :tenantId "
          + "AND pc.partner.id = :partnerId "
          + "AND pc.role = :role "
          + "AND pc.primaryContact = true "
          + "AND pc.isActive = true")
  List<PartnerContact> findActivePrimaryByTenantIdAndPartnerIdAndRole(
      @Param("tenantId") UUID tenantId,
      @Param("partnerId") UUID partnerId,
      @Param("role") PartnerContactRole role);
}
