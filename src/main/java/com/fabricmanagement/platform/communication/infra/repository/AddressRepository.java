package com.fabricmanagement.platform.communication.infra.repository;

import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Address entity. */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

  /** Find all active addresses by type within tenant. */
  @Query(
      "SELECT a FROM Address a WHERE a.tenantId = :tenantId "
          + "AND a.addressType = :addressType AND a.isActive = true")
  List<Address> findByTenantIdAndAddressType(
      @Param("tenantId") UUID tenantId, @Param("addressType") AddressType addressType);

  /** Find active addresses by city within tenant. */
  @Query(
      "SELECT a FROM Address a WHERE a.tenantId = :tenantId "
          + "AND a.city = :city AND a.isActive = true")
  List<Address> findByTenantIdAndCity(@Param("tenantId") UUID tenantId, @Param("city") String city);

  /** Find active addresses by country within tenant. */
  @Query(
      "SELECT a FROM Address a WHERE a.tenantId = :tenantId "
          + "AND a.country = :country AND a.isActive = true")
  List<Address> findByTenantIdAndCountry(
      @Param("tenantId") UUID tenantId, @Param("country") String country);
}
