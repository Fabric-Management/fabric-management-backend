package com.fabricmanagement.user_service.repository;

import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Temel sorgular
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Soft delete dahil sorguları
    @Query("SELECT u FROM User u WHERE u.deleted = false")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.deleted = false")
    Optional<User> findByUsernameAndNotDeleted(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deleted = false")
    Optional<User> findByEmailAndNotDeleted(@Param("email") String email);

    // Sayfalama ile sorgular
    @Query("SELECT u FROM User u WHERE u.deleted = false")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.companyId = :companyId AND u.deleted = false")
    Page<User> findByCompanyId(@Param("companyId") UUID companyId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deleted = false")
    Page<User> findByStatus(@Param("status") UserStatus status, Pageable pageable);

    // Role bazlı sorgular
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role AND u.deleted = false")
    Page<User> findByRole(@Param("role") Role role, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r IN :roles AND u.deleted = false")
    Page<User> findByRolesIn(@Param("roles") List<Role> roles, Pageable pageable);

    // Arama sorguları
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND u.deleted = false")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    // Şirket ve arama kombinasyonu
    @Query("SELECT u FROM User u WHERE " +
            "u.companyId = :companyId AND " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND u.deleted = false")
    Page<User> searchUsersByCompany(@Param("companyId") UUID companyId,
                                    @Param("search") String search,
                                    Pageable pageable);

    // İstatistik sorguları
    @Query("SELECT COUNT(u) FROM User u WHERE u.companyId = :companyId AND u.deleted = false")
    long countByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.deleted = false")
    long countByStatus(@Param("status") UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :startDate AND u.deleted = false")
    long countNewUsersSince(@Param("startDate") LocalDateTime startDate);

    // Güvenlik sorguları
    @Query("SELECT u FROM User u WHERE u.lockedUntil < :now AND u.status = 'LOCKED'")
    List<User> findUsersToUnlock(@Param("now") LocalDateTime now);

    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :date AND u.deleted = false")
    List<User> findInactiveUsersSince(@Param("date") LocalDateTime date);

    // Bulk operations
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
    int updateStatusForUsers(@Param("ids") List<UUID> ids, @Param("status") UserStatus status);

    @Modifying
    @Query("UPDATE User u SET u.deleted = true, u.deletedAt = :now WHERE u.id IN :ids")
    int softDeleteUsers(@Param("ids") List<UUID> ids, @Param("now") LocalDateTime now);

    // Email verification
    @Modifying
    @Query("UPDATE User u SET u.emailVerified = true WHERE u.id = :userId")
    int markEmailAsVerified(@Param("userId") UUID userId);

    // Password operations
    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.hasPassword = true, u.passwordChangedAt = :now WHERE u.id = :userId")
    int updatePassword(@Param("userId") UUID userId,
                       @Param("passwordHash") String passwordHash,
                       @Param("now") LocalDateTime now);

    // Login tracking
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now, u.failedLoginAttempts = 0 WHERE u.id = :userId")
    int updateLastLogin(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    int incrementFailedLoginAttempts(@Param("userId") UUID userId);
}