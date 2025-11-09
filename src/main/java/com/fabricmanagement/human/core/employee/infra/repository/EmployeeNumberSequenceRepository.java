package com.fabricmanagement.human.core.employee.infra.repository;

import com.fabricmanagement.human.core.employee.domain.EmployeeNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeNumberSequenceRepository extends JpaRepository<EmployeeNumberSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EmployeeNumberSequence s WHERE s.tenantId = :tenantId")
    Optional<EmployeeNumberSequence> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    @Query("SELECT s FROM EmployeeNumberSequence s WHERE s.tenantId = :tenantId")
    Optional<EmployeeNumberSequence> findByTenantId(@Param("tenantId") UUID tenantId);
}

