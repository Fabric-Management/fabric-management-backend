package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Address entity.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    /**
     * Find all addresses by type within tenant.
     */
    @Query("SELECT a FROM Address a WHERE a.tenantId = :tenantId AND a.addressType = :addressType")
    List<Address> findByTenantIdAndAddressType(
            @Param("tenantId") UUID tenantId,
            @Param("addressType") AddressType addressType);

    /**
     * Find primary addresses by type within tenant.
     */
    @Query("SELECT a FROM Address a WHERE a.tenantId = :tenantId " +
           "AND a.addressType = :addressType AND a.isPrimary = true")
    List<Address> findPrimaryByTenantIdAndAddressType(
            @Param("tenantId") UUID tenantId,
            @Param("addressType") AddressType addressType);

    /**
     * Find addresses by city within tenant.
     */
    @Query("SELECT a FROM Address a WHERE a.tenantId = :tenantId AND a.city = :city")
    List<Address> findByTenantIdAndCity(
            @Param("tenantId") UUID tenantId,
            @Param("city") String city);

    /**
     * Find addresses by country within tenant.
     */
    @Query("SELECT a FROM Address a WHERE a.tenantId = :tenantId AND a.country = :country")
    List<Address> findByTenantIdAndCountry(
            @Param("tenantId") UUID tenantId,
            @Param("country") String country);
}

