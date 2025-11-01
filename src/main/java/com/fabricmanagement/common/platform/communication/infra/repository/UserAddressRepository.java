package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import com.fabricmanagement.common.platform.communication.domain.UserAddressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserAddress junction entity.
 */
@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UserAddressId> {

    /**
     * Find all addresses for a user within tenant.
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.tenantId = :tenantId AND ua.userId = :userId")
    List<UserAddress> findByTenantIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId);

    /**
     * Find specific user-address assignment.
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.addressId = :addressId")
    Optional<UserAddress> findByUserIdAndAddressId(
            @Param("userId") UUID userId,
            @Param("addressId") UUID addressId);

    /**
     * Find primary address for user.
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.isPrimary = true")
    Optional<UserAddress> findPrimaryByUserId(@Param("userId") UUID userId);

    /**
     * Find work addresses for user.
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.userId = :userId AND ua.isWorkAddress = true")
    List<UserAddress> findWorkAddressesByUserId(@Param("userId") UUID userId);
}

