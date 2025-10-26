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
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    /**
     * Complete password setup using secure token.
     *
     * <p><b>Simple flow:</b> Token only (email verified by click)</p>
     * <p>No verification code needed - email link click proves ownership.</p>
     *
     * @param request Password setup request
     * @return Login response with tokens and onboarding status
     */
    @Transactional
    public LoginResponse setupPassword(PasswordSetupRequest request) {
        log.info("Password setup initiated: token={}", request.getToken().substring(0, 8) + "...");

        RegistrationToken token = tokenRepository.findByToken(request.getToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid registration token"));

        if (!token.isValid()) {
            log.warn("Token invalid or expired: token={}", request.getToken().substring(0, 8) + "...");
            throw new IllegalArgumentException("Registration token is invalid or expired");
        }

        UserDto user = userFacade.findByContactValue(token.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (authUserRepository.existsByContactValue(token.getContactValue())) {
            throw new IllegalArgumentException("User already has password set");
        }

        AuthUser authUser = AuthUser.builder()
            .contactValue(user.getContactValue())
            .contactType(user.getContactType())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .isVerified(true)
            .build();
        authUser.setIsActive(true);
        authUser.setTenantId(user.getTenantId());
        authUserRepository.save(authUser);

        token.markAsUsed();
        tokenRepository.save(token);

        UserDto freshUser = userFacade.findById(user.getTenantId(), user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = jwtService.generateAccessToken(freshUser);
        String refreshToken = jwtService.generateRefreshToken(freshUser);

        eventPublisher.publish(new UserRegisteredEvent(
            user.getTenantId(),
            user.getId(),
            user.getContactValue()
        ));

        log.info("✅ Password setup completed: user={}, needsOnboarding={}",
            PiiMaskingUtil.maskEmail(user.getContactValue()),
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

