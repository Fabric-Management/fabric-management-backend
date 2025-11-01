package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Password Setup Service - Complete registration with secure token.
 *
 * <h2>Two Flows:</h2>
 * <ul>
 *   <li><b>Sales-led:</b> Token only (email verified by click)</li>
 *   <li><b>Self-service:</b> Token + verification code (double security)</li>
 * </ul>
 *
 * <p>Both flows result in:</p>
 * <ul>
 *   <li>Password set</li>
 *   <li>User verified</li>
 *   <li>Auto-login (JWT tokens returned)</li>
 *   <li>Onboarding status check</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupService {

    private final RegistrationTokenRepository tokenRepository;
    private final AuthUserRepository authUserRepository;
    private final UserFacade userFacade;
    private final UserRepository userRepository;
    private final UserContactService userContactService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    /**
     * Complete password setup using secure token.
     *
     * <p><b>Two flows supported:</b></p>
     * <ul>
     *   <li><b>Sales-led:</b> Token only (email verified by click)</li>
     *   <li><b>Self-service:</b> Token + verification code (double security)</li>
     * </ul>
     *
     * @param request Password setup request
     * @return Login response with tokens and onboarding status
     */
    @Transactional
    public LoginResponse setupPassword(PasswordSetupRequest request) {
        log.info("Password setup initiated: token={}..., tokenType={}",
            request.getToken().substring(0, Math.min(8, request.getToken().length())),
            "checking...");

        RegistrationToken token = tokenRepository.findByToken(request.getToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid registration token"));

        if (!token.isValid()) {
            log.warn("Token invalid or expired: token={}", request.getToken().substring(0, 8) + "...");
            throw new IllegalArgumentException("Registration token is invalid or expired");
        }

        log.debug("Token type: {}", token.getTokenType());

        // Both SALES_LED and SELF_SERVICE tokens work the same way:
        // Email link click = email verified (no verification code needed)
        // Verification codes are only needed for unverified contacts during login flows

        UserDto user = userFacade.findByContactValue(token.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (authUserRepository.existsByContactValue(token.getContactValue())) {
            throw new IllegalArgumentException("User already has password set");
        }

        // Get User entity to find authentication contact
        com.fabricmanagement.common.platform.user.domain.User userEntity = 
            userRepository.findByTenantIdAndId(user.getTenantId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

        // Get primary authentication contact
        UUID contactId = userContactService.getAuthenticationContact(userEntity.getId())
            .map(uc -> uc.getContactId())
            .orElseThrow(() -> new IllegalStateException("User has no authentication contact"));

        // Check if AuthUser already exists for this contact
        if (authUserRepository.existsByContactId(contactId)) {
            throw new IllegalArgumentException("User already has password set");
        }

        // Create AuthUser with Contact entity (new system)
        String passwordHash = passwordEncoder.encode(request.getPassword());
        AuthUser authUser = AuthUser.create(contactId, passwordHash);
        authUser.setTenantId(user.getTenantId());
        authUser.verify();
        authUserRepository.save(authUser);

        token.markAsUsed();
        tokenRepository.save(token);

        // Reload User entity with contacts/departments for JWT generation
        com.fabricmanagement.common.platform.user.domain.User freshUserEntity = 
            userRepository.findByTenantIdAndId(user.getTenantId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = jwtService.generateAccessToken(freshUserEntity);
        String refreshToken = jwtService.generateRefreshToken(freshUserEntity);

        // Get contact value for event
        String contactValue = freshUserEntity.getPrimaryContact()
            .map(contact -> contact.getContactValue())
            .orElse(token.getContactValue());

        eventPublisher.publish(new UserRegisteredEvent(
            user.getTenantId(),
            user.getId(),
            contactValue
        ));

        UserDto freshUser = UserDto.from(freshUserEntity);

        log.info("✅ Password setup completed: user={}, needsOnboarding={}",
            PiiMaskingUtil.maskEmail(contactValue),
            !user.getHasCompletedOnboarding());

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(900L)
            .user(freshUser)
            .needsOnboarding(!user.getHasCompletedOnboarding())
            .build();
    }
}

