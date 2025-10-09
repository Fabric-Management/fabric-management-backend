package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.aggregate.Department;
import com.fabricmanagement.shared.domain.policy.DepartmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Department Repository
 * 
 * Database operations for departments
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    
    @Query("SELECT d FROM Department d WHERE d.companyId = :companyId AND d.deleted = false")
    List<Department> findByCompanyId(@Param("companyId") UUID companyId);
    
    @Query("SELECT d FROM Department d WHERE d.companyId = :companyId AND d.code = :code AND d.deleted = false")
    Optional<Department> findByCompanyIdAndCode(@Param("companyId") UUID companyId, @Param("code") String code);
    
    @Query("SELECT d FROM Department d WHERE d.companyId = :companyId AND d.type = :type AND d.deleted = false")
    List<Department> findByCompanyIdAndType(@Param("companyId") UUID companyId, @Param("type") DepartmentType type);
    
    @Query("SELECT d FROM Department d WHERE d.managerId = :managerId AND d.deleted = false")
    List<Department> findByManagerId(@Param("managerId") UUID managerId);
    
    @Query("SELECT d FROM Department d WHERE d.companyId = :companyId AND d.active = true AND d.deleted = false")
    List<Department> findActiveByCompanyId(@Param("companyId") UUID companyId);
}

