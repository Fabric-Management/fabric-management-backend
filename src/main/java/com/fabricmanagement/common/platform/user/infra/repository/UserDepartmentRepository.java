package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.common.platform.user.domain.UserDepartmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserDepartment junction entity.
 *
 * <p>Manages Many-to-Many relationships between User and Department.</p>
 */
@Repository
public interface UserDepartmentRepository extends JpaRepository<UserDepartment, UserDepartmentId> {

    List<UserDepartment> findByUserId(UUID userId);

    List<UserDepartment> findByDepartmentId(UUID departmentId);

    Optional<UserDepartment> findByUserIdAndDepartmentId(UUID userId, UUID departmentId);

    Optional<UserDepartment> findByUserIdAndIsPrimaryTrue(UUID userId);

    @Query("SELECT ud FROM UserDepartment ud " +
           "JOIN ud.user u " +
           "WHERE u.tenantId = :tenantId AND ud.userId = :userId")
    List<UserDepartment> findByTenantIdAndUserId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query("SELECT ud FROM UserDepartment ud " +
           "JOIN ud.user u " +
           "WHERE u.tenantId = :tenantId AND ud.departmentId = :departmentId")
    List<UserDepartment> findByTenantIdAndDepartmentId(@Param("tenantId") UUID tenantId, @Param("departmentId") UUID departmentId);

    void deleteByUserIdAndDepartmentId(UUID userId, UUID departmentId);
}

