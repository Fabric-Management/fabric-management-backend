package com.fabricmanagement.user.domain.repository;

import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.valueobject.TenantId;
import com.fabricmanagement.user.domain.valueobject.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);

    List<User> findByTenantId(UUID tenantId);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    void deleteById(UUID id);
}