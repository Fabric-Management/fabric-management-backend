package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.UserWorkLocation;
import com.fabricmanagement.platform.user.domain.UserWorkLocationId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserWorkLocationRepository
    extends JpaRepository<UserWorkLocation, UserWorkLocationId> {

  @Query(
      "SELECT wl FROM UserWorkLocation wl "
          + "LEFT JOIN FETCH wl.organizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "LEFT JOIN FETCH oa.organization "
          + "WHERE wl.tenantId = :tenantId AND wl.userId = :userId "
          + "AND wl.isActive = true")
  List<UserWorkLocation> findByTenantIdAndUserId(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "SELECT wl FROM UserWorkLocation wl "
          + "LEFT JOIN FETCH wl.organizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "WHERE wl.userId = :userId AND wl.orgAddressId = :orgAddressId")
  Optional<UserWorkLocation> findByUserIdAndOrgAddressId(
      @Param("userId") UUID userId, @Param("orgAddressId") UUID orgAddressId);

  @Query(
      "SELECT wl FROM UserWorkLocation wl "
          + "LEFT JOIN FETCH wl.organizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "LEFT JOIN FETCH oa.organization "
          + "WHERE wl.userId = :userId AND wl.isPrimary = true "
          + "AND wl.isActive = true")
  Optional<UserWorkLocation> findPrimaryByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT wl FROM UserWorkLocation wl "
          + "LEFT JOIN FETCH wl.user "
          + "WHERE wl.orgAddressId = :orgAddressId "
          + "AND wl.isActive = true")
  List<UserWorkLocation> findByOrgAddressId(@Param("orgAddressId") UUID orgAddressId);

  @Query(
      "SELECT wl FROM UserWorkLocation wl "
          + "LEFT JOIN FETCH wl.organizationAddress oa "
          + "LEFT JOIN FETCH oa.address "
          + "WHERE wl.tenantId = :tenantId AND wl.userId IN :userIds AND wl.isPrimary = true "
          + "AND wl.isActive = true")
  List<UserWorkLocation> findPrimaryByTenantIdAndUserIdIn(
      @Param("tenantId") UUID tenantId, @Param("userIds") List<UUID> userIds);

  @Query("SELECT COUNT(wl) FROM UserWorkLocation wl WHERE wl.orgAddressId = :orgAddressId")
  long countByOrgAddressId(@Param("orgAddressId") UUID orgAddressId);

  @Modifying
  @Query("DELETE FROM UserWorkLocation wl WHERE wl.orgAddressId = :orgAddressId")
  void deleteAllByOrgAddressId(@Param("orgAddressId") UUID orgAddressId);
}
