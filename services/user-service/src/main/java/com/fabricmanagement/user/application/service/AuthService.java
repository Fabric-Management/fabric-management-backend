package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.*;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import com.fabricmanagement.user.api.dto.*;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication Service
 *
 * Handles user authentication operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final ContactServiceClient contactServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger auditLogger;

    // Configurable response time masking (timing attack prevention)
    @Value("${security.response-time-masking.min-response-time-ms:200}")
    private long minResponseTimeMs;

    /**
     * Check if contact exists and whether user has password
     * 
     * Security: Response time masking to prevent timing attacks.
     * Always responds in minimum 200ms to prevent enumeration via response time analysis.
     */
    @Transactional(readOnly = true)
    public CheckContactResponse checkContact(CheckContactRequest request) {
        long startTime = System.currentTimeMillis();
        log.debug("Checking contact: {}", request.getContactValue());

        try {
            // Find contact in contact service
            ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(request.getContactValue());
            
            if (response == null || response.getData() == null) {
                return buildResponseWithTimingMask(
                    startTime,
                    CheckContactResponse.builder()
                        .exists(false)
                        .hasPassword(false)
                        .message("This contact is not registered. Please contact your administrator.")
                        .build()
                );
            }

            // Get user by owner_id from contact
            ContactDto contact = response.getData();
            UUID userId = contact.getOwnerId(); // Already UUID, no conversion needed

            User user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElse(null);

            if (user == null) {
                return buildResponseWithTimingMask(
                    startTime,
                    CheckContactResponse.builder()
                        .exists(false)
                        .hasPassword(false)
                        .message("User not found. Please contact your administrator.")
                        .build()
                );
            }

            boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isEmpty();

            return buildResponseWithTimingMask(
                startTime,
                CheckContactResponse.builder()
                    .exists(true)
                    .hasPassword(hasPassword)
                    .userId(user.getId().toString())
                    .message(hasPassword ?
                            "Please enter your password" :
                            "Please create your password")
                    .build()
            );

        } catch (Exception e) {
            log.error("Error checking contact: {}", e.getMessage(), e);
            return buildResponseWithTimingMask(
                startTime,
                CheckContactResponse.builder()
                    .exists(false)
                    .hasPassword(false)
                    .message("An error occurred. Please try again.")
                    .build()
            );
        }
    }

    /**
     * Masks response time to prevent timing attacks
     * Ensures minimum response time (configurable via application.yml)
     * 
     * @param startTime Request start time in milliseconds
     * @param response Response to return
     * @return Response after timing mask applied
     */
    private CheckContactResponse buildResponseWithTimingMask(long startTime, CheckContactResponse response) {
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

    /**
     * Setup initial password for user
     * 
     * Validations:
     * - Contact must exist
     * - Contact must be verified
     * - User must exist and not deleted
     * - User status must be PENDING_VERIFICATION or ACTIVE
     * - Password must not be already set
     */
    @Transactional
    public void setupPassword(SetupPasswordRequest request) {
        log.info("Setting up password for contact: {}", request.getContactValue());

        // 1. Find and validate contact
        ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(request.getContactValue());
        if (response == null || response.getData() == null) {
            throw new ContactNotFoundException(request.getContactValue());
        }

        ContactDto contact = response.getData();
        
        // 2. Validate contact is verified
        if (!contact.isVerified()) {
            throw new ContactNotVerifiedException(request.getContactValue());
        }

        UUID userId = contact.getOwnerId(); // Already UUID, no conversion needed

        // 3. Get and validate user
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        // 4. Validate user status (must be PENDING_VERIFICATION or ACTIVE for password setup)
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION && 
            user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidUserStatusException(
                user.getStatus().name(), 
                "PENDING_VERIFICATION or ACTIVE"
            );
        }

        // 5. Check if password already exists
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            throw new PasswordAlreadySetException(request.getContactValue());
        }

        // 6. Set password and activate user
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPasswordHash(hashedPassword);
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedBy(user.getId().toString());

        userRepository.save(user);

        // 7. Audit log
        auditLogger.logPasswordSetup(request.getContactValue(), userId.toString());
        
        log.info("Password setup completed successfully for user: {}", userId);
    }

    /**
     * Login user with contact and password
     * 
     * Security features:
     * - Account lockout after 5 failed attempts (15 minutes)
     * - Generic "Invalid credentials" message to prevent user enumeration
     * - Failed attempt tracking in Redis (distributed)
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String contactValue = request.getContactValue();
        log.info("Login attempt for contact: {}", contactValue);

        try {
            // 0. Check if account is locked
            loginAttemptService.checkIfLocked(contactValue);

            // 1. Find contact
            ApiResponse<ContactDto> response = contactServiceClient.findByContactValue(contactValue);
            if (response == null || response.getData() == null) {
                loginAttemptService.recordFailedAttempt(contactValue);
                auditLogger.logFailedLogin(contactValue, "Contact not found");
                throw new InvalidPasswordException("Invalid credentials");
            }

            ContactDto contact = response.getData();
            UUID userId = contact.getOwnerId(); // Already UUID, no conversion needed

            // 2. Get user
            User user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> {
                        loginAttemptService.recordFailedAttempt(contactValue);
                        auditLogger.logFailedLogin(contactValue, "User not found or deleted");
                        return new InvalidPasswordException("Invalid credentials");
                    });

            // 3. Check password exists
            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                throw new PasswordAlreadySetException("Password not set. Please setup your password first.");
            }

            // 4. Validate password
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Failed login attempt for contact: {}", contactValue);
                loginAttemptService.recordFailedAttempt(contactValue);
                auditLogger.logFailedLogin(contactValue, "Invalid password");
                throw new InvalidPasswordException("Invalid credentials");
            }

            // 5. Check user status
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidUserStatusException("User account is not active: " + user.getStatus());
            }

            // 6. Clear failed attempts on successful login
            loginAttemptService.clearFailedAttempts(contactValue);

            // 7. Update last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // 8. Generate tokens
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());
            claims.put("firstName", user.getFirstName());
            claims.put("lastName", user.getLastName());
            claims.put("companyId", user.getCompanyId() != null ? user.getCompanyId().toString() : null);

            String accessToken = jwtTokenProvider.generateToken(
                    user.getId().toString(),
                    user.getTenantId().toString(), // JWT requires String for JSON serialization
                    claims
            );

            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getId().toString(),
                    user.getTenantId().toString() // JWT requires String for JSON serialization
            );

            // 9. Audit log
            auditLogger.logSuccessfulLogin(contactValue, userId.toString(), user.getTenantId().toString());
            
            log.info("Login successful for user: {}", userId);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(user.getId())
                    .tenantId(user.getTenantId()) // Already UUID, no conversion needed
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(contact.getContactValue())
                    .role(user.getRole())
                    .build();
                    
        } catch (DomainException e) {
            // Re-throw domain exceptions (including AccountLockedException)
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during login for contact: {}", contactValue, e);
            loginAttemptService.recordFailedAttempt(contactValue);
            auditLogger.logFailedLogin(contactValue, "Unexpected error: " + e.getMessage());
            throw new InvalidPasswordException("Invalid credentials");
        }
    }
}
