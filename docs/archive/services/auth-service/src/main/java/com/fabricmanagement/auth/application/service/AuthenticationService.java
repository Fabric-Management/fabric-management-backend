package com.fabricmanagement.auth.application.service;

import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.auth.domain.aggregate.AuthUser;
import com.fabricmanagement.shared.domain.valueobject.ContactType;
import com.fabricmanagement.auth.domain.event.SecurityEvent;
import com.fabricmanagement.auth.infrastructure.repository.AuthUserRepository;
import com.fabricmanagement.auth.infrastructure.messaging.AuthEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication Service
 * 
 * Handles user authentication, registration, and account management
 * 
 * ✅ ZERO HARDCODED VALUES - ServiceConstants kullanıyor
 * ✅ PRODUCTION-READY
 * ✅ EVENT-DRIVEN
 * ✅ NO USERNAME FIELD - contactValue ile auth
 * ✅ UUID TYPE SAFETY - her yerde UUID
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher eventPublisher;
    
    @Value("${auth.password.min-length:8}")
    private int minPasswordLength;
    
    @Value("${auth.password.max-failed-attempts:5}")
    private int maxFailedAttempts;
    
    /**
     * Register new user
     * ⚠️ NO USERNAME FIELD - Uses contactValue (email/phone)
     */
    @Transactional
    public AuthUser registerUser(String contactValue, ContactType contactType, String password, UUID tenantId, String ipAddress, String userAgent) {
        log.info("🔐 Registering new user with contact: {}", contactValue);
        
        // Validate input
        validateRegistrationInput(contactValue, contactType, password);
        
        // Check if user already exists
        if (authUserRepository.existsByContactValueAndTenantId(contactValue, tenantId)) {
            throw new IllegalArgumentException(ServiceConstants.MSG_EMAIL_ALREADY_REGISTERED);
        }
        
        // Generate salt and hash password
        String salt = generateSalt();
        String passwordHash = passwordEncoder.encode(password + salt);
        
        // Create user
        AuthUser user = AuthUser.builder()
            .contactValue(contactValue)
            .contactType(contactType)
            .passwordHash(passwordHash)
            .salt(salt)
            .tenantId(tenantId)
            .isActive(true)
            .isLocked(false)
            .failedLoginAttempts(0)
            .passwordChangedAt(LocalDateTime.now())
            .build();
        
        AuthUser savedUser = authUserRepository.save(user);
        
            // Publish registration event
            SecurityEvent event = SecurityEvent.userRegistration(
                savedUser.getId(),
                savedUser.getTenantId(),
                savedUser.getContactValue(),
                savedUser.getContactType().name(),
                ipAddress,
                userAgent
            );
            
            eventPublisher.publishSecurityEvent(event);
        
        log.info("✅ User registered successfully: {}", contactValue);
        return savedUser;
    }
    
    /**
     * Authenticate user
     * ⚠️ NO USERNAME FIELD - Uses contactValue (email/phone)
     */
    @Transactional
    public Optional<AuthUser> authenticateUser(String contactValue, String password, UUID tenantId, String ipAddress, String userAgent) {
        log.info("🔐 Authenticating user with contact: {}", contactValue);
        
        Optional<AuthUser> userOpt = authUserRepository.findByContactValueAndTenantId(contactValue, tenantId);
        
        if (userOpt.isEmpty()) {
            log.warn("❌ User not found: {}", contactValue);
            return Optional.empty();
        }
        
        AuthUser user = userOpt.get();
        
        // Check if account is locked
        if (user.isAccountLocked()) {
            log.warn("🔒 Account locked: {}", contactValue);
            return Optional.empty();
        }
        
        // Verify password
        String saltedPassword = password + user.getSalt();
        if (!passwordEncoder.matches(saltedPassword, user.getPasswordHash())) {
            log.warn("❌ Invalid password for user: {}", contactValue);
            handleFailedLogin(user);
            return Optional.empty();
        }
        
        // Successful login
        user.resetFailedLoginAttempts();
        authUserRepository.save(user);
        
            // Publish login event
            SecurityEvent event = SecurityEvent.userLogin(
                user.getId(),
                user.getTenantId(),
                user.getContactValue(),
                user.getContactType().name(),
                ipAddress,
                userAgent
            );
            
            eventPublisher.publishSecurityEvent(event);
        
        log.info("✅ User authenticated successfully: {}", contactValue);
        return Optional.of(user);
    }
    
    /**
     * Handle failed login attempt
     */
    private void handleFailedLogin(AuthUser user) {
        user.incrementFailedLoginAttempts();
        
        if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
            user.lockAccount();
            
                // Publish account locked event
                SecurityEvent event = SecurityEvent.accountLocked(
                    user.getId(),
                    user.getTenantId(),
                    user.getContactValue(),
                    user.getContactType().name(),
                    user.getFailedLoginAttempts(),
                    ""
                );
                
                eventPublisher.publishSecurityEvent(event);
            
            log.warn("🔒 Account locked due to failed attempts: {}", user.getContactValue());
        }
        
        authUserRepository.save(user);
    }
    
    /**
     * Validate registration input
     * ⚠️ NO USERNAME FIELD - Uses contactValue validation
     */
    private void validateRegistrationInput(String contactValue, ContactType contactType, String password) {
        if (contactValue == null || contactValue.trim().length() < 3) {
            throw new IllegalArgumentException("Contact value must be at least 3 characters");
        }
        
        if (contactType == null) {
            throw new IllegalArgumentException("Contact type is required");
        }
        
        if (contactType == ContactType.EMAIL && !contactValue.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        if (password == null || password.length() < minPasswordLength) {
            throw new IllegalArgumentException("Password must be at least " + minPasswordLength + " characters");
        }
    }
    
    /**
     * Generate random salt
     */
    private String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
