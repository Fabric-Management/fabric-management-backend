package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.UserAddress;
import com.fabricmanagement.common.platform.user.domain.UserAddressId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for UserAddress junction entity (User module). */
@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UserAddressId> {

  @Query("SELECT ua FROM UserAddress ua WHERE ua.tenantId = :tenantId AND ua.userId = :userId")
  List<UserAddress> findByTenantIdAndUserId(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.addressId = :addressId")
  Optional<UserAddress> findByUserIdAndAddressId(
      @Param("userId") UUID userId, @Param("addressId") UUID addressId);

  @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.isPrimary = true")
  Optional<UserAddress> findPrimaryByUserId(@Param("userId") UUID userId);

  @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.isWorkAddress = true")
  List<UserAddress> findWorkAddressesByUserId(@Param("userId") UUID userId);
}
