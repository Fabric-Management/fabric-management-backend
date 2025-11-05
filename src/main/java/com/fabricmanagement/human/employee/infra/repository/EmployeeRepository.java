package com.fabricmanagement.human.employee.infra.repository;

import com.fabricmanagement.human.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Employee repository for HR data.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /**
     * Find employee by user ID (One-to-One relationship).
     */
    Optional<Employee> findByUserId(UUID userId);

    /**
     * Find employee by tenant ID and user ID.
     */
    @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.userId = :userId")
    Optional<Employee> findByTenantIdAndUserId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * Find employee by tenant ID and employee number.
     */
    @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.employeeNumber = :employeeNumber")
    Optional<Employee> findByTenantIdAndEmployeeNumber(
        @Param("tenantId") UUID tenantId, 
        @Param("employeeNumber") String employeeNumber
    );

    /**
     * Check if employee exists for user.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Find all employees by tenant ID.
     */
    @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId")
    java.util.List<Employee> findByTenantId(@Param("tenantId") UUID tenantId);
}

