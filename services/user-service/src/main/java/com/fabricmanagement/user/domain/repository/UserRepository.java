package com.fabricmanagement.user.domain.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.valueobject.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user profile management.
 * Delegates to identity-service for core user operations.
 */
public interface UserRepository {
    
    Optional<User> findById(UserId userId);
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    User save(User user);
    
    void deleteById(UserId userId);
    
    boolean existsById(UserId userId);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
}

