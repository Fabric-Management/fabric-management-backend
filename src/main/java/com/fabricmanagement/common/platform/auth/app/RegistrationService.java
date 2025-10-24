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
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

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
    private final AuthUserRepository authUserRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;

    @Value("${application.verification.code-length:6}")
    private int codeLength;

    @Value("${application.verification.code-expiry-minutes:10}")
    private int codeExpiryMinutes;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    @Transactional
    public String checkEligibilityAndSendCode(RegisterCheckRequest request) {
        log.info("Checking registration eligibility: contactValue={}", request.getContactValue());

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElse(null);

        if (user == null) {
            log.warn("Contact not found in system: {}", request.getContactValue());
            return "Your information is not registered. Our representative will contact you.";
        }

        if (authUserRepository.existsByContactValue(request.getContactValue())) {
            log.warn("Contact already registered: {}", request.getContactValue());
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

        // TODO: Send via Communication module (multi-channel)
        log.info("Verification code generated: contactValue={}, code={} (TODO: Send via Communication)",
            request.getContactValue(), code);

        return "Verification code sent. Please check your messages.";
    }

    @Transactional
    public LoginResponse verifyAndRegister(VerifyAndRegisterRequest request) {
        log.info("Verifying and registering: contactValue={}", request.getContactValue());

        VerificationCode verificationCode = verificationCodeRepository
            .findByContactValueAndCodeAndType(
                request.getContactValue(),
                request.getCode(),
                VerificationType.REGISTRATION
            )
            .orElseThrow(() -> new IllegalArgumentException("Invalid verification code"));

        if (!verificationCode.isValid()) {
            log.warn("Invalid or expired verification code: contactValue={}", request.getContactValue());
            throw new IllegalArgumentException("Verification code is invalid or expired");
        }

        verificationCode.markAsUsed();
        verificationCodeRepository.save(verificationCode);

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String passwordHash = passwordEncoder.encode(request.getPassword());

        AuthUser authUser = AuthUser.create(
            user.getContactValue(),
            user.getContactType(),
            passwordHash
        );
        authUser.setTenantId(user.getTenantId());
        authUser.verify();
        authUserRepository.save(authUser);

        String accessToken = jwtService.generateAccessToken(
            convertToUserEntity(user)
        );
        String refreshToken = jwtService.generateRefreshToken(
            convertToUserEntity(user)
        );

        eventPublisher.publish(new UserRegisteredEvent(
            user.getTenantId(),
            user.getId(),
            user.getContactValue()
        ));

        log.info("User registered successfully: userId={}, uid={}", user.getId(), user.getUid());

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(900L)
            .user(user)
            .build();
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt((int) Math.pow(10, codeLength));
        return String.format("%0" + codeLength + "d", code);
    }

    private com.fabricmanagement.common.platform.user.domain.User convertToUserEntity(UserDto dto) {
        com.fabricmanagement.common.platform.user.domain.User user = 
            com.fabricmanagement.common.platform.user.domain.User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .contactValue(dto.getContactValue())
                .contactType(dto.getContactType())
                .companyId(dto.getCompanyId())
                .department(dto.getDepartment())
                .build();
        
        user.setId(dto.getId());
        user.setTenantId(dto.getTenantId());
        user.setUid(dto.getUid());
        user.setIsActive(dto.getIsActive());
        
        return user;
    }
}

