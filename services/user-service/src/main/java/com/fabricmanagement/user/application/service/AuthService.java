package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.*;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.infrastructure.util.MaskingUtil;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordWithVerificationRequest;
import com.fabricmanagement.user.api.dto.request.SendVerificationRequest;
import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.application.mapper.AuthMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import com.fabricmanagement.user.infrastructure.security.LoginAttemptTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication Service
 * 
 * Handles user authentication, password setup, and contact validation.
 * 
 * Pattern: @Lazy injection for ContactServiceClient to prevent circular dependency
 * - AuthController → AuthService → ContactServiceClient (Feign)
 * - FeignClient initialization triggers SecurityConfig
 * - SecurityConfig creates filters that scan Controllers
 * - Lazy loading breaks the cycle
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ContactServiceClient contactServiceClient;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptTracker loginAttemptTracker;
    private final SecurityAuditLogger auditLogger;
    private final MaskingUtil maskingUtil;
    
    // ✅ Manual constructor with @Lazy for ContactServiceClient
    public AuthService(
            UserRepository userRepository,
            @Lazy ContactServiceClient contactServiceClient,  // ✅ Lazy to break circular dependency
            AuthMapper authMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptTracker loginAttemptTracker,
            SecurityAuditLogger auditLogger,
            MaskingUtil maskingUtil) {
        this.userRepository = userRepository;
        this.contactServiceClient = contactServiceClient;
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptTracker = loginAttemptTracker;
        this.auditLogger = auditLogger;
        this.maskingUtil = maskingUtil;
    }

    @Value("${security.response-time-masking.min-response-time-ms:200}")
    private long minResponseTimeMs;

    @Transactional(readOnly = true)
    public CheckContactResponse checkContact(CheckContactRequest request) {
        long startTime = System.currentTimeMillis();
        log.debug("Checking contact: {}", request.getContactValue());

        try {
            ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(request.getContactValue());
            
            if (response == null || response.getData() == null) {
                return applyTimingMask(startTime, authMapper.toNotFoundResponse());
            }

            ContactDto contact = response.getData();
            UUID userId = contact.getOwnerId();

            User user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElse(null);

            if (user == null) {
                return applyTimingMask(startTime, authMapper.toUserNotFoundResponse());
            }

            CheckContactResponse checkResponse = authMapper.toCheckResponse(user);
            
            // Add verified status and next step
            checkResponse.setVerified(contact.isVerified());
            checkResponse.setMaskedContact(maskingUtil.maskEmail(request.getContactValue()));
            
            // Determine next step for UI
            if (!contact.isVerified()) {
                checkResponse.setNextStep(ServiceConstants.NEXT_STEP_SEND_VERIFICATION);
            } else if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                checkResponse.setNextStep(ServiceConstants.NEXT_STEP_SETUP_PASSWORD);
            } else {
                checkResponse.setNextStep(ServiceConstants.NEXT_STEP_LOGIN);
            }
            
            return applyTimingMask(startTime, checkResponse);

        } catch (Exception e) {
            log.error("Error checking contact: {}", e.getMessage(), e);
            return applyTimingMask(startTime, authMapper.toErrorResponse());
        }
    }

    private CheckContactResponse applyTimingMask(long startTime, CheckContactResponse response) {
        long elapsed = System.currentTimeMillis() - startTime;

        if (elapsed < minResponseTimeMs) {
            try {
                Thread.sleep(minResponseTimeMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Response time masking interrupted", e);
            }
        }

        return response;
    }

    @Transactional
    public void setupPassword(SetupPasswordRequest request) {
        log.info("Setting up password for contact: {}", request.getContactValue());

        ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(request.getContactValue());
        if (response == null || response.getData() == null) {
            throw new ContactNotFoundException(request.getContactValue());
        }

        ContactDto contact = response.getData();
        
        if (!contact.isVerified()) {
            throw new ContactNotVerifiedException(request.getContactValue());
        }

        UUID userId = contact.getOwnerId();

        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION && 
            user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidUserStatusException(
                user.getStatus().name(), 
                ServiceConstants.USER_STATUS_PENDING_OR_ACTIVE
            );
        }

        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            throw new PasswordAlreadySetException(request.getContactValue());
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(hashedPassword);
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedBy(user.getId().toString());

        userRepository.save(user);

        auditLogger.logPasswordSetup(request.getContactValue(), userId.toString());
        
        log.info("Password setup completed successfully for user: {}", userId);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String contactValue = request.getContactValue();
        log.info("Login attempt for contact: {}", contactValue);

        try {
            loginAttemptTracker.checkIfLocked(contactValue);

            ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(contactValue);
            if (response == null || response.getData() == null) {
                loginAttemptTracker.recordFailedAttempt(contactValue);
                auditLogger.logFailedLogin(contactValue, ServiceConstants.MSG_CONTACT_NOT_FOUND);
                throw new InvalidPasswordException(ServiceConstants.MSG_INVALID_CREDENTIALS);
            }

            ContactDto contact = response.getData();
            UUID userId = contact.getOwnerId();

            User user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> {
                        loginAttemptTracker.recordFailedAttempt(contactValue);
                        auditLogger.logFailedLogin(contactValue, ServiceConstants.MSG_USER_NOT_FOUND);
                        return new InvalidPasswordException(ServiceConstants.MSG_INVALID_CREDENTIALS);
                    });

            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                throw new PasswordAlreadySetException(ServiceConstants.MSG_PASSWORD_NOT_SET);
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Failed login attempt for contact: {}", contactValue);
                loginAttemptTracker.recordFailedAttempt(contactValue);
                auditLogger.logFailedLogin(contactValue, ServiceConstants.MSG_INVALID_CREDENTIALS);
                throw new InvalidPasswordException(ServiceConstants.MSG_INVALID_CREDENTIALS);
            }

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidUserStatusException(ServiceConstants.MSG_USER_ACCOUNT_NOT_ACTIVE + ": " + user.getStatus());
            }

            loginAttemptTracker.clearFailedAttempts(contactValue);

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            Map<String, Object> claims = authMapper.buildJwtClaims(user);

            String accessToken = jwtTokenProvider.generateToken(
                    user.getId().toString(),
                    user.getTenantId().toString(),
                    claims
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getId().toString(),
                    user.getTenantId().toString()
            );

            auditLogger.logSuccessfulLogin(contactValue, userId.toString(), user.getTenantId().toString());
            
            log.info("Login successful for user: {}", userId);

            return authMapper.toLoginResponse(user, contact, accessToken, refreshToken);
                    
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login for contact: {}", contactValue, e);
            loginAttemptTracker.recordFailedAttempt(contactValue);
            auditLogger.logFailedLogin(contactValue, "Unexpected error: " + e.getMessage());
            throw new InvalidPasswordException(ServiceConstants.MSG_INVALID_CREDENTIALS);
        }
    }
    
    /**
     * Send verification code to unverified contact
     * Used when user clicks "Create Password" but contact is not verified
     */
    @Transactional
    public void sendVerificationCode(SendVerificationRequest request) {
        log.info("Sending verification code to: {}", request.getContactValue());
        
        // Find contact
        ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(request.getContactValue());
        if (response == null || response.getData() == null) {
            throw new ContactNotFoundException(request.getContactValue());
        }
        
        ContactDto contact = response.getData();
        
        // Check if already verified
        if (contact.isVerified()) {
            log.warn("Contact already verified: {}", request.getContactValue());
            return; // No-op, already verified
        }
        
        // Send verification code via Contact Service
        try {
            contactServiceClient.sendVerificationCode(contact.getId());
            log.info("✅ Verification code sent to: {}", request.getContactValue());
        } catch (Exception e) {
            log.error("Failed to send verification code: {}", e.getMessage(), e);
            throw new RuntimeException(ServiceConstants.MSG_FAILED_TO_SEND_VERIFICATION_CODE);
        }
    }
    
    /**
     * Atomic operation: Verify contact + Setup password + Auto-login
     * 
     * This is the OPTIMIZED flow (3 operations in 1 HTTP request):
     * 1. Verify contact with code
     * 2. Setup password
     * 3. Generate JWT and auto-login
     * 
     * Performance: 3 HTTP calls → 1 HTTP call (66% latency reduction)
     */
    @Transactional
    public LoginResponse setupPasswordWithVerification(SetupPasswordWithVerificationRequest request) {
        log.info("Setup password with verification for: {}", request.getContactValue());
        
        // Step 1: Find contact
        ApiResponse<ContactDto> contactResponse = contactServiceClient.findByContactValue(request.getContactValue());
        if (contactResponse == null || contactResponse.getData() == null) {
            throw new ContactNotFoundException(request.getContactValue());
        }
        
        ContactDto contact = contactResponse.getData();
        
        // Step 2: Verify contact with code (call Contact Service)
        try {
            contactServiceClient.verifyContact(contact.getId(), request.getVerificationCode());
            log.info("✅ Contact verified: {}", request.getContactValue());
        } catch (Exception e) {
            log.error("Verification failed: {}", e.getMessage());
            throw new RuntimeException(ServiceConstants.MSG_INVALID_VERIFICATION_CODE);
        }
        
        // Step 3: Get user
        User user = userRepository.findById(contact.getOwnerId())
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new UserNotFoundException(contact.getOwnerId().toString()));
        
        // Step 4: Check if password already exists
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            throw new PasswordAlreadySetException(request.getContactValue());
        }
        
        // Step 5: Setup password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(hashedPassword);
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedBy(user.getId().toString());
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        auditLogger.logPasswordSetup(request.getContactValue(), user.getId().toString());
        
        // Step 6: Generate JWT tokens (auto-login)
        Map<String, Object> claims = authMapper.buildJwtClaims(user);
        
        String accessToken = jwtTokenProvider.generateToken(
            user.getId().toString(),
            user.getTenantId().toString(),
            claims
        );
        
        String refreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId().toString(),
            user.getTenantId().toString()
        );
        
        auditLogger.logSuccessfulLogin(request.getContactValue(), user.getId().toString(), user.getTenantId().toString());
        
        log.info("✅ Password setup + auto-login successful for user: {}", user.getId());
        
        // Step 7: Return login response (direct to dashboard)
        return authMapper.toLoginResponse(user, contact, accessToken, refreshToken);
    }
}
