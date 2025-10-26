package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Login Service - Authentication logic.
 *
 * <h2>Flow:</h2>
 * <ol>
 *   <li>Validate credentials (contact + password)</li>
 *   <li>Check user status (verified, active, not locked)</li>
 *   <li>Generate tokens (access + refresh)</li>
 *   <li>Publish login event</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserFacade userFacade;
    private final CompanyFacade companyFacade;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        log.info("Login attempt: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        AuthUser authUser = authUserRepository.findByContactValue(request.getContactValue())
            .orElseThrow(() -> createContextAwareNotFoundException(request.getContactValue()));

        if (authUser.isLocked()) {
            log.warn("Account locked: contactValue={}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            throw new IllegalArgumentException("Account is temporarily locked. Try again later.");
        }

        if (!authUser.getIsVerified()) {
            log.warn("Account not verified: contactValue={}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            throw new IllegalArgumentException("Account not verified. Please complete registration.");
        }

        if (!authUser.getIsActive()) {
            log.warn("Account not active: contactValue={}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()));
            throw new IllegalArgumentException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
            authUser.recordFailedLogin();
            authUserRepository.save(authUser);
            
            log.warn("Invalid password: contactValue={}, attempts={}", 
                PiiMaskingUtil.maskEmail(request.getContactValue()), 
                authUser.getFailedLoginAttempts());
            
            throw new IllegalArgumentException("Invalid credentials");
        }

        UserDto user = userFacade.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getIsActive()) {
            throw new IllegalArgumentException("User account is deactivated");
        }

        String accessToken = jwtService.generateAccessToken(
            convertToUserEntity(user)
        );
        String refreshToken = jwtService.generateRefreshToken(
            convertToUserEntity(user)
        );

        RefreshToken refreshTokenEntity = RefreshToken.create(
            user.getId(),
            refreshToken,
            Instant.now().plusMillis(refreshTokenExpiration)
        );
        refreshTokenRepository.save(refreshTokenEntity);

        authUser.recordSuccessfulLogin();
        authUserRepository.save(authUser);

        eventPublisher.publish(new UserLoginEvent(
            user.getTenantId(),
            user.getId(),
            user.getContactValue(),
            ipAddress
        ));

        log.info("Login successful: userId={}, uid={}", user.getId(), user.getUid());

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(900L) // 15 minutes in seconds
            .user(user)
            .build();
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

    /**
     * Create context-aware user not found exception.
     *
     * <p>Provides helpful error messages based on context:</p>
     * <ul>
     *   <li>Known tenant company user: "Contact your IT team"</li>
     *   <li>Known supplier company user: "Contact your customer representative"</li>
     *   <li>Unknown: "Sign up at fabricmanagement.com"</li>
     * </ul>
     */
    private IllegalArgumentException createContextAwareNotFoundException(String contactValue) {
        String domain = extractEmailDomain(contactValue);
        if (domain == null) {
            return new IllegalArgumentException("User not found. Please check your credentials.");
        }

        Optional<UserDto> anyUserWithDomain = userFacade.findByContactValue(
            contactValue.substring(0, 1) + "@" + domain
        );

        if (anyUserWithDomain.isPresent()) {
            UserDto existingUser = anyUserWithDomain.get();
            Optional<CompanyDto> company = companyFacade.findById(
                existingUser.getTenantId(),
                existingUser.getCompanyId()
            );

            if (company.isPresent()) {
                CompanyDto companyDto = company.get();
                
                if (companyDto.getIsTenant()) {
                    return new IllegalArgumentException(
                        "User not found. If you're a " + companyDto.getCompanyName() + 
                        " employee, please contact your IT team or manager to add you to the system."
                    );
                } else {
                    return new IllegalArgumentException(
                        "User not found. Please contact your customer representative at " + 
                        companyDto.getCompanyName() + " to add you to the system."
                    );
                }
            }
        }

        return new IllegalArgumentException(
            "User not found. If you're a new customer, please sign up at fabricmanagement.com"
        );
    }

    /**
     * Extract domain from email address.
     */
    private String extractEmailDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String[] parts = email.split("@");
        return parts.length == 2 ? parts[1] : null;
    }
}

