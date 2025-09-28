package com.fabricmanagement.identity.infrastructure.persistence.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import com.fabricmanagement.identity.infrastructure.persistence.entity.UserEntity;
import com.fabricmanagement.identity.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of UserRepository.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(UserId id) {
        log.debug("Finding user by id: {}", id);
        return jpaRepository.findById(id.getValue())
            .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("Finding user by username: {}", username);
        return jpaRepository.findByUsername(username)
            .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return jpaRepository.findByEmail(email)
            .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByContactValue(String contactValue) {
        log.debug("Finding user by contact value: {}", contactValue);
        return jpaRepository.findByContactValue(contactValue)
            .map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        log.debug("Saving user: {}", user.getId());
        UserEntity entity = userMapper.toEntity(user);
        UserEntity savedEntity = jpaRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    @Transactional
    public void deleteById(UserId id) {
        log.debug("Deleting user by id: {}", id);
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByUsername(String username) {
        log.debug("Checking if user exists by username: {}", username);
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        log.debug("Checking if user exists by email: {}", email);
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByContactValue(String contactValue) {
        log.debug("Checking if user exists by contact value: {}", contactValue);
        return jpaRepository.existsByContactValue(contactValue);
    }
}