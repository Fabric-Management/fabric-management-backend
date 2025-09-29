package com.fabricmanagement.user.infrastructure.persistence.repository;

import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import com.fabricmanagement.user.domain.repository.UserRepository;
import com.fabricmanagement.user.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of UserRepository using JPA.
 * Bridges between domain layer and infrastructure layer.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        log.debug("Saving user with ID: {}", user.getId());
        UserEntity entity = userMapper.toEntity(user);
        UserEntity savedEntity = userJpaRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<User> findByIdAndTenantId(UUID id, UUID tenantId) {
        log.debug("Finding user by ID: {} and tenant: {}", id, tenantId);
        return userJpaRepository.findByIdAndTenantId(id, tenantId)
            .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByIdentityId(UUID identityId) {
        log.debug("Finding user by identity ID: {}", identityId);
        return userJpaRepository.findByIdentityId(identityId)
            .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByIdentityIdAndTenantId(UUID identityId, UUID tenantId) {
        log.debug("Finding user by identity ID: {} and tenant: {}", identityId, tenantId);
        return userJpaRepository.findByIdentityIdAndTenantId(identityId, tenantId)
            .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByIdentityId(UUID identityId) {
        log.debug("Checking if user exists by identity ID: {}", identityId);
        return userJpaRepository.existsByIdentityId(identityId);
    }

    @Override
    public boolean existsByIdentityIdAndTenantId(UUID identityId, UUID tenantId) {
        log.debug("Checking if user exists by identity ID: {} and tenant: {}", identityId, tenantId);
        return userJpaRepository.existsByIdentityIdAndTenantId(identityId, tenantId);
    }

    @Override
    public Page<User> findActiveUsersByTenantId(UUID tenantId, Pageable pageable) {
        log.debug("Finding active users for tenant: {}", tenantId);
        Page<UserEntity> entityPage = userJpaRepository.findActiveUsersByTenantId(tenantId, pageable);
        return entityPage.map(userMapper::toDomain);
    }

    @Override
    public List<User> findByTenantIdAndStatus(UUID tenantId, UserStatus status) {
        log.debug("Finding users by tenant: {} and status: {}", tenantId, status);
        return userJpaRepository.findByTenantIdAndStatusAndDeletedFalse(tenantId, status)
            .stream()
            .map(userMapper::toDomain)
            .toList();
    }

    @Override
    public List<User> findByTenantIdAndDepartment(UUID tenantId, String department) {
        log.debug("Finding users by tenant: {} and department: {}", tenantId, department);
        return userJpaRepository.findByTenantIdAndDepartmentAndDeletedFalse(tenantId, department)
            .stream()
            .map(userMapper::toDomain)
            .toList();
    }

    @Override
    public Page<User> searchUsers(UUID tenantId, String searchQuery, Pageable pageable) {
        log.debug("Searching users for tenant: {} with query: {}", tenantId, searchQuery);
        Page<UserEntity> entityPage = userJpaRepository.searchUsers(tenantId, searchQuery, pageable);
        return entityPage.map(userMapper::toDomain);
    }

    @Override
    public boolean existsByIdAndTenantId(UUID id, UUID tenantId) {
        log.debug("Checking if user exists by ID: {} and tenant: {}", id, tenantId);
        return userJpaRepository.existsByIdAndTenantIdAndDeletedFalse(id, tenantId);
    }

    @Override
    public long countActiveUsersByTenantId(UUID tenantId) {
        log.debug("Counting active users for tenant: {}", tenantId);
        return userJpaRepository.countActiveUsersByTenantId(tenantId);
    }

    @Override
    public void deleteByIdAndTenantId(UUID id, UUID tenantId) {
        log.debug("Soft deleting user by ID: {} and tenant: {}", id, tenantId);
        userJpaRepository.findByIdAndTenantId(id, tenantId)
            .ifPresent(entity -> {
                entity.markAsDeleted();
                userJpaRepository.save(entity);
            });
    }

    @Override
    public Page<User> findByTenantId(UUID tenantId, Pageable pageable) {
        log.debug("Finding all users for tenant: {}", tenantId);
        Page<UserEntity> entityPage = userJpaRepository.findByTenantIdAndDeletedFalse(tenantId, pageable);
        return entityPage.map(userMapper::toDomain);
    }
}

