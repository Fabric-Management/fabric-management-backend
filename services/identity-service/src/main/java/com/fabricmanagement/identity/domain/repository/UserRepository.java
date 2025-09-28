package com.fabricmanagement.identity.domain.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.valueobject.UserId;

import java.util.Optional;

/**
 * Repository interface for User aggregate root.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByContactValue(String contactValue);

    User save(User user);

    void deleteById(UserId id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByContactValue(String contactValue);
}