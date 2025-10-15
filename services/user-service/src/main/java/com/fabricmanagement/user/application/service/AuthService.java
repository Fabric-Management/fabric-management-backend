package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.*;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
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
    
    // ✅ Manual constructor with @Lazy for ContactServiceClient
    public AuthService(
            UserRepository userRepository,
            @Lazy ContactServiceClient contactServiceClient,  // ✅ Lazy to break circular dependency
            AuthMapper authMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptTracker loginAttemptTracker,
            SecurityAuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.contactServiceClient = contactServiceClient;
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptTracker = loginAttemptTracker;
        this.auditLogger = auditLogger;
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

            return applyTimingMask(startTime, authMapper.toCheckResponse(user));

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
                "PENDING_VERIFICATION or ACTIVE"
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
                throw new InvalidUserStatusException("User account is not active: " + user.getStatus());
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
}
