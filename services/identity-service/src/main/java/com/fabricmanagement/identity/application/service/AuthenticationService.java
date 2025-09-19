package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.Credentials;
import com.fabricmanagement.identity.domain.valueobject.Role;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user authentication and identity management.
 * Handles user creation, password management, and authentication logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * Creates a new user with initial credentials.
     */
    public User createUser(String username, String email, String firstName, String lastName, 
                          String password, Role role, UUID tenantId) {
        log.info("Creating new user: {}", username);
        
        // Check if user already exists
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists: " + username);
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists: " + email);
        }
        
        // Create user entity
        User user = User.builder()
            .userId(UserId.generate())
            .username(username)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .role(role)
            .tenantId(tenantId)
            .status(UserStatus.PENDING_ACTIVATION)
            .credentials(Credentials.builder()
                .passwordHash(passwordEncoder.encode(password))
                .passwordCreatedAt(LocalDateTime.now())
                .passwordMustChange(false)
                .build())
            .build();
        
        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUserId());
        
        return savedUser;
    }
    
    /**
     * Authenticates a user with username/email and password.
     */
    @Transactional(readOnly = true)
    public Optional<User> authenticateUser(String usernameOrEmail, String password) {
        log.debug("Authenticating user: {}", usernameOrEmail);
        
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(usernameOrEmail);
        
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", usernameOrEmail);
            return Optional.empty();
        }
        
        User user = userOpt.get();
        
        // Check if user is active
        if (!user.isActive()) {
            log.warn("User account is not active: {}", usernameOrEmail);
            return Optional.empty();
        }
        
        // Check if user is locked
        if (user.isLocked()) {
            log.warn("User account is locked: {}", usernameOrEmail);
            return Optional.empty();
        }
        
        // Verify password
        if (!passwordEncoder.matches(password, user.getCredentials().getPasswordHash())) {
            log.warn("Invalid password for user: {}", usernameOrEmail);
            return Optional.empty();
        }
        
        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("User authenticated successfully: {}", usernameOrEmail);
        return Optional.of(user);
    }
    
    /**
     * Changes user password.
     */
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        log.info("Changing password for user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getCredentials().getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }
        
        // Update password
        user.getCredentials().setPasswordHash(passwordEncoder.encode(newPassword));
        user.getCredentials().setPasswordChangedAt(LocalDateTime.now());
        user.getCredentials().setPasswordMustChange(false);
        
        User savedUser = userRepository.save(user);
        log.info("Password changed successfully for user: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Resets user password (admin operation).
     */
    public User resetPassword(UUID userId, String newPassword) {
        log.info("Resetting password for user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        // Update password
        user.getCredentials().setPasswordHash(passwordEncoder.encode(newPassword));
        user.getCredentials().setPasswordChangedAt(LocalDateTime.now());
        user.getCredentials().setPasswordMustChange(true);
        
        User savedUser = userRepository.save(user);
        log.info("Password reset successfully for user: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Activates a user account.
     */
    public User activateUser(UUID userId) {
        log.info("Activating user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        user.setStatus(UserStatus.ACTIVE);
        user.setIsActive(true);
        
        User savedUser = userRepository.save(user);
        log.info("User activated successfully: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Deactivates a user account.
     */
    public User deactivateUser(UUID userId) {
        log.info("Deactivating user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        user.setStatus(UserStatus.SUSPENDED);
        user.setIsActive(false);
        
        User savedUser = userRepository.save(user);
        log.info("User deactivated successfully: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Locks a user account.
     */
    public User lockUser(UUID userId) {
        log.info("Locking user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusHours(24)); // Lock for 24 hours
        
        User savedUser = userRepository.save(user);
        log.info("User locked successfully: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Unlocks a user account.
     */
    public User unlockUser(UUID userId) {
        log.info("Unlocking user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        
        User savedUser = userRepository.save(user);
        log.info("User unlocked successfully: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Gets user by ID.
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(UUID userId) {
        log.debug("Fetching user by ID: {}", userId);
        return userRepository.findByUserId(userId);
    }
    
    /**
     * Gets user by username or email.
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsernameOrEmail(String usernameOrEmail) {
        log.debug("Fetching user by username or email: {}", usernameOrEmail);
        return userRepository.findByUsernameOrEmail(usernameOrEmail);
    }
}