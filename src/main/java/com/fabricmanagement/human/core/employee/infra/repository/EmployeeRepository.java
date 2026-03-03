package com.fabricmanagement.human.core.employee.infra.repository;

import com.fabricmanagement.human.core.employee.domain.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  Optional<Employee> findByUserId(UUID userId);

  @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.userId = :userId")
  Optional<Employee> findByTenantIdAndUserId(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.employeeNumber = :employeeNumber")
  Optional<Employee> findByTenantIdAndEmployeeNumber(
      @Param("tenantId") UUID tenantId, @Param("employeeNumber") String employeeNumber);

  @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId AND e.userId IN :userIds")
  List<Employee> findByTenantIdAndUserIdIn(
      @Param("tenantId") UUID tenantId, @Param("userIds") Collection<UUID> userIds);

  boolean existsByUserId(UUID userId);

  @Query("SELECT e FROM Employee e WHERE e.tenantId = :tenantId")
  List<Employee> findByTenantId(@Param("tenantId") UUID tenantId);
}
