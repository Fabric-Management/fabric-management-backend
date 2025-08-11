package com.fabricmanagement.user_service.repository;

import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import com.fabricmanagement.user_service.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomUserRepository {

    /**
     * Gelişmiş filtreleme ile kullanıcı arama
     */
    Page<User> findUsersWithFilters(
            UUID companyId,
            List<Role> roles,
            List<UserStatus> statuses,
            String search,
            Boolean emailVerified,
            Boolean hasPassword,
            Pageable pageable
    );

    /**
     * Kullanıcı istatistikleri
     */
    UserStatistics getUserStatistics(UUID companyId);

    /**
     * Rol bazlı kullanıcı sayıları
     */
    List<RoleCount> countUsersByRole(UUID companyId);

    // DTO records for statistics
    record UserStatistics(
            long totalUsers,
            long activeUsers,
            long verifiedUsers,
            long usersWithPassword,
            long lockedUsers,
            long newUsersThisMonth
    ) {}

    record RoleCount(
            Role role,
            long count
    ) {}
}