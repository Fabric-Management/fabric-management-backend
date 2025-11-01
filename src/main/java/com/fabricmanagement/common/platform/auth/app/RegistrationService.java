package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.VerificationCode;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.VerificationCodeRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.VerificationService;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Registration Service - User registration with multi-channel verification.
 *
 * <h2>Registration Flow:</h2>
 * <pre>
 * Step 1: Check Eligibility
 *   ├─ Contact MUST exist in User table (pre-approved)
 *   ├─ Contact NOT already verified
 *   └─ Generate & send verification code
 *
 * Step 2: Verify & Register
 *   ├─ Validate verification code
 *   ├─ Hash password (BCrypt)
 *   ├─ Create AuthUser
 *   ├─ Generate JWT tokens
 *   └─ Publish UserRegisteredEvent
 * </pre>
 *
 * <h2>Multi-Channel Verification:</h2>
 * <p>Priority: WhatsApp → Email → SMS (handled by Communication module)</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserFacade userFacade;
    private final UserRepository userRepository;
    private final AuthUserRepository authUserRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;
    private final VerificationService verificationService;
    private final ContactService contactService;
    private final UserContactService userContactService;

    @Value("${application.verification.code-length:6}")
    private int codeLength;

    @Value("${application.verification.code-expiry-minutes:10}")
    private int codeExpiryMinutes;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    @Transactional
    public String checkEligibilityAndSendCode(RegisterCheckRequest request) {
        log.info("Checking registration eligibility: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElse(null);

        if (user == null) {
            log.warn("Contact not found in system: {}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            return "Your information is not registered. Our representative will contact you.";
        }

        if (authUserRepository.existsByContactValue(request.getContactValue())) {
            log.warn("Contact already registered: {}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            return "This account is already registered. Please login.";
        }

        String code = generateVerificationCode();

        verificationCodeRepository.deleteByContactValueAndType(
            request.getContactValue(),
            VerificationType.REGISTRATION
        );

        VerificationCode verificationCode = VerificationCode.create(
            request.getContactValue(),
            code,
            VerificationType.REGISTRATION,
            codeExpiryMinutes
        );
        verificationCodeRepository.save(verificationCode);

        // Send verification code via multi-channel (WhatsApp → Email → SMS)
        try {
            verificationService.sendVerificationCode(request.getContactValue(), code);
            log.info("✅ Verification code sent successfully to: {}", request.getContactValue());
        } catch (Exception e) {
            log.error("❌ Failed to send verification code to: {}", request.getContactValue(), e);
            // Continue anyway - code is in database, user can try again
        }

        return "Verification code sent. Please check your email.";
    }

    @Transactional
    public LoginResponse verifyAndRegister(VerifyAndRegisterRequest request) {
        log.info("Verifying and registering: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        VerificationCode verificationCode = verificationCodeRepository
            .findByContactValueAndCodeAndType(
                request.getContactValue(),
                request.getCode(),
                VerificationType.REGISTRATION
            )
            .orElseThrow(() -> new IllegalArgumentException("Invalid verification code"));

        if (!verificationCode.isValid()) {
            log.warn("Invalid or expired verification code: contactValue={}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            throw new IllegalArgumentException("Verification code is invalid or expired");
        }

        verificationCode.markAsUsed();
        verificationCodeRepository.save(verificationCode);

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Find Contact entity for authentication
        com.fabricmanagement.common.platform.communication.domain.Contact contact = 
            findAuthenticationContact(user.getId(), request.getContactValue())
                .orElseThrow(() -> new IllegalStateException("Authentication contact not found for user"));

        String passwordHash = passwordEncoder.encode(request.getPassword());

        // Create AuthUser with Contact entity (new system)
        AuthUser authUser = AuthUser.create(
            contact.getId(),
            passwordHash
        );
        authUser.setTenantId(user.getTenantId());
        authUser.verify();
        authUserRepository.save(authUser);

        // Get User entity for JWT generation (needs contacts/departments loaded)
        com.fabricmanagement.common.platform.user.domain.User userEntity = 
            userRepository.findByTenantIdAndId(user.getTenantId(), user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found after registration"));

        String accessToken = jwtService.generateAccessToken(userEntity);
        String refreshToken = jwtService.generateRefreshToken(userEntity);

        String contactValue = contact.getContactValue();
        eventPublisher.publish(new UserRegisteredEvent(
            user.getTenantId(),
            user.getId(),
            contactValue
        ));

        log.info("User registered successfully: userId={}, uid={}", user.getId(), user.getUid());

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(900L)
            .user(user)
            .build();
    }

    /**
     * Find authentication contact for user by contactValue.
     * <p>Uses UserContact junction table to find Contact entity with isForAuthentication=true.</p>
     */
    private Optional<com.fabricmanagement.common.platform.communication.domain.Contact> 
            findAuthenticationContact(UUID userId, String contactValue) {
        return userContactService.getAuthenticationContact(userId)
            .flatMap(uc -> contactService.findById(uc.getContactId()))
            .filter(c -> c.getContactValue().equals(contactValue));
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt((int) Math.pow(10, codeLength));
        return String.format("%0" + codeLength + "d", code);
    }

}

