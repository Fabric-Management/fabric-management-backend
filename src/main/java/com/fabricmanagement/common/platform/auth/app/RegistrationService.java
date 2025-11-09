package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.app.VerificationCodeManager.IssuedVerificationCode;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.VerificationService;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;
    private final VerificationService verificationService;
    private final ContactService contactService;
    private final UserContactService userContactService;
    private final VerificationCodeManager verificationCodeManager;

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

        IssuedVerificationCode issuedCode;
        try {
            issuedCode = verificationCodeManager.issueCode(
                request.getContactValue(),
                VerificationType.REGISTRATION
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Verification code issuance throttled: contactValue={}, reason={}",
                PiiMaskingUtil.maskEmail(request.getContactValue()), ex.getMessage());
            return ex.getMessage();
        }

        verificationService.sendVerificationCode(request.getContactValue(), issuedCode.code());
        log.info("Verification code queued for sending (async) to: {}",
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        return "Verification code sent. Please check your email.";
    }

    @Transactional
    public LoginResponse verifyAndRegister(VerifyAndRegisterRequest request) {
        log.info("Verifying and registering: contactValue={}",
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        verificationCodeManager.validateAndConsume(
            request.getContactValue(),
            VerificationType.REGISTRATION,
            request.getCode()
        );

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        com.fabricmanagement.common.platform.communication.domain.Contact contact = findUserContactByValue(
            user.getId(),
            request.getContactValue()
        );

        contactService.verifyContact(contact.getId());

        String passwordHash = passwordEncoder.encode(request.getPassword());

        AuthUser authUser = AuthUser.create(
            contact.getId(),
            passwordHash
        );
        authUser.setTenantId(user.getTenantId());
        authUser.verify();
        authUserRepository.save(authUser);

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

    private com.fabricmanagement.common.platform.communication.domain.Contact findUserContactByValue(
            UUID userId,
            String contactValue) {
        return userContactService.getUserContacts(userId).stream()
            .map(uc -> contactService.findById(uc.getContactId()))
            .flatMap(Optional::stream)
            .filter(c -> c.getContactValue().equals(contactValue))
            .findFirst()
            .or(() -> contactService.findByValue(contactValue))
            .orElseThrow(() -> new IllegalStateException("Authentication contact not found for user"));
    }

}

