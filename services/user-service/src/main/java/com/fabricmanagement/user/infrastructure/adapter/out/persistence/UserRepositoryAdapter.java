package com.fabricmanagement.user.infrastructure.adapter.out.persistence;

import com.fabricmanagement.user.application.port.out.UserRepositoryPort;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import com.fabricmanagement.user.infrastructure.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.fabricmanagement.user.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper persistenceMapper;

    @Override
    public Optional<User> findByIdAndTenantId(UUID id, UUID tenantId) {
        log.debug("Finding user by id: {} and tenantId: {}", id, tenantId);
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(persistenceMapper::toDomainModel);
    }

    @Override
    public User save(User user) {
        log.debug("Saving user: {}", user.getUsername());

        UserJpaEntity entity;
        if (user.getId() != null) {
            // Update existing
            entity = jpaRepository.findById(user.getId())
                    .orElse(persistenceMapper.toJpaEntity(user));
            persistenceMapper.updateJpaEntity(user, entity);
        } else {
            // Create new
            entity = persistenceMapper.toJpaEntity(user);
        }

        UserJpaEntity savedEntity = jpaRepository.save(entity);
        log.info("User saved successfully with id: {}", savedEntity.getId());

        return persistenceMapper.toDomainModel(savedEntity);
    }

    @Override
    public boolean existsByUsernameAndTenantId(String username, UUID tenantId) {
        log.debug("Checking if user exists with username: {} and tenantId: {}", username, tenantId);
        return jpaRepository.existsByUsernameAndTenantId(username, tenantId);
    }
}