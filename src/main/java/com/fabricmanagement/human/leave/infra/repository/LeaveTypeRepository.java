package com.fabricmanagement.human.leave.infra.repository;

import com.fabricmanagement.human.leave.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    @Query("""
        select lt from LeaveType lt
        where lt.tenantId = :tenantId
          and lt.code = :code
          and lt.active = true
        """)
    Optional<LeaveType> findActiveByCode(@Param("tenantId") UUID tenantId, @Param("code") String code);

    @Query("""
        select lt from LeaveType lt
        where lt.tenantId = :tenantId
          and lt.active = true
          and (lt.countryCode is null or lt.countryCode = :countryCode)
        """)
    List<LeaveType> findActiveForCountry(@Param("tenantId") UUID tenantId, @Param("countryCode") String countryCode);
}

