package com.fabricmanagement.identity.domain.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.valueobject.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User aggregate.
 * Simple interface with only necessary methods.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(String username);

    Optional<User> findByContact(String contactValue);

    boolean existsByUsername(String username);

    boolean existsByContact(String contactValue);

    void deleteById(UserId id);
}