package com.fabricmanagement.human.employee.infra.repository;

import com.fabricmanagement.human.employee.domain.EmployeeNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Employee Number Sequence.
 * 
 * <p>Uses pessimistic locking (SELECT FOR UPDATE) to ensure atomic sequence increments.</p>
 */
@Repository
public interface EmployeeNumberSequenceRepository extends JpaRepository<EmployeeNumberSequence, UUID> {

    /**
     * Find sequence by tenant with pessimistic lock.
     * 
     * <p><b>CRITICAL:</b> Uses SELECT FOR UPDATE to prevent race conditions.</p>
     * <p>This ensures only one transaction can increment the sequence at a time.</p>
     * 
     * @param tenantId Tenant ID
     * @return Sequence entity (locked)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EmployeeNumberSequence s WHERE s.tenantId = :tenantId")
    Optional<EmployeeNumberSequence> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    /**
     * Find sequence by tenant (read-only, no lock).
     */
    @Query("SELECT s FROM EmployeeNumberSequence s WHERE s.tenantId = :tenantId")
    Optional<EmployeeNumberSequence> findByTenantId(@Param("tenantId") UUID tenantId);
}

