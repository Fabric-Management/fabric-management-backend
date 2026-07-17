package com.fabricmanagement.production.masterdata.color.infra.repository;

import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ColorPartnerRefRepository extends JpaRepository<ColorPartnerRef, UUID> {

  Page<ColorPartnerRef> findByTenantIdAndColorId(UUID tenantId, UUID colorId, Pageable pageable);

  @Query(
      """
      select distinct ref
      from ColorPartnerRef ref
      left join fetch ref.codes
      where ref.tenantId = :tenantId
        and ref.id in :ids
      """)
  List<ColorPartnerRef> findWithCodesByTenantIdAndIdIn(
      @Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);

  @EntityGraph(attributePaths = "codes")
  Optional<ColorPartnerRef> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
  @EntityGraph(attributePaths = "codes")
  @Query(
      "select distinct ref from ColorPartnerRef ref left join ref.codes "
          + "where ref.tenantId = :tenantId and ref.id = :id")
  Optional<ColorPartnerRef> findForMutationByTenantIdAndId(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  boolean existsByTenantIdAndColorIdAndPartnerIdAndRole(
      UUID tenantId, UUID colorId, UUID partnerId, PartnerRole role);

  @Query(
      """
      select count(code) > 0
      from ColorPartnerCode code
      where code.tenantId = :tenantId
        and code.partnerId = :partnerId
        and code.role = :role
        and code.externalCodeKey = :externalCodeKey
        and code.isActive = true
      """)
  boolean existsActiveCode(
      @Param("tenantId") UUID tenantId,
      @Param("partnerId") UUID partnerId,
      @Param("role") PartnerRole role,
      @Param("externalCodeKey") String externalCodeKey);

  @Query(
      """
      select code
      from ColorPartnerCode code
      join fetch code.colorPartnerRef ref
      where code.tenantId = :tenantId
        and code.partnerId = :partnerId
        and code.role = :role
        and code.externalCodeKey = :externalCodeKey
        and code.isActive = true
        and ref.isActive = true
        and exists (
          select color.id from Color color
          where color.tenantId = :tenantId
            and color.id = ref.colorId
            and color.isActive = true
        )
      """)
  Optional<ColorPartnerCode> findActiveCodeForReverseLookup(
      @Param("tenantId") UUID tenantId,
      @Param("partnerId") UUID partnerId,
      @Param("role") PartnerRole role,
      @Param("externalCodeKey") String externalCodeKey);

  @Query(
      """
      select code
      from ColorPartnerCode code
      join fetch code.colorPartnerRef ref
      where code.tenantId = :tenantId
        and ref.colorId = :colorId
        and ref.partnerId = :partnerId
        and ref.role = :role
        and code.isActive = true
        and code.primary = true
        and ref.isActive = true
        and exists (
          select color.id from Color color
          where color.tenantId = :tenantId
            and color.id = ref.colorId
            and color.isActive = true
        )
      """)
  Optional<ColorPartnerCode> findActivePrimaryForForwardLookup(
      @Param("tenantId") UUID tenantId,
      @Param("colorId") UUID colorId,
      @Param("partnerId") UUID partnerId,
      @Param("role") PartnerRole role);
}
