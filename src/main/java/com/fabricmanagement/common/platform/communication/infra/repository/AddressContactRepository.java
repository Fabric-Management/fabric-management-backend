package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import com.fabricmanagement.common.platform.communication.domain.AddressContactId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AddressContact junction entity.
 */
@Repository
public interface AddressContactRepository extends JpaRepository<AddressContact, AddressContactId> {

    /**
     * Find all contacts for an address within tenant.
     */
    @Query("SELECT ac FROM AddressContact ac WHERE ac.tenantId = :tenantId AND ac.addressId = :addressId")
    List<AddressContact> findByTenantIdAndAddressId(
            @Param("tenantId") UUID tenantId,
            @Param("addressId") UUID addressId);

    /**
     * Find specific address-contact assignment.
     */
    @Query("SELECT ac FROM AddressContact ac WHERE ac.addressId = :addressId AND ac.contactId = :contactId")
    Optional<AddressContact> findByAddressIdAndContactId(
            @Param("addressId") UUID addressId,
            @Param("contactId") UUID contactId);

    /**
     * Find primary contact for address.
     */
    @Query("SELECT ac FROM AddressContact ac WHERE ac.addressId = :addressId AND ac.isPrimary = true")
    Optional<AddressContact> findPrimaryByAddressId(@Param("addressId") UUID addressId);

    /**
     * Find all contacts for multiple addresses (batch query).
     */
    @Query("SELECT ac FROM AddressContact ac WHERE ac.tenantId = :tenantId AND ac.addressId IN :addressIds")
    List<AddressContact> findByTenantIdAndAddressIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("addressIds") List<UUID> addressIds);
}

