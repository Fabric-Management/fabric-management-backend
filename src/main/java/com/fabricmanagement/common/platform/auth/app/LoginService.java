package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.VerificationService;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.auth.app.VerificationCodeManager.IssuedVerificationCode;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final UserRepository userRepository;
    private final CompanyFacade companyFacade;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;
    private final ContactService contactService;
    private final UserContactService userContactService;
    private final VerificationService verificationService;
    private final VerificationCodeManager verificationCodeManager;

    @Value("${application.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        log.info("Login attempt: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        // ✅ Check if contact exists and belongs to a user
        Optional<AuthUser> authUserOpt = authUserRepository.findByContactValue(request.getContactValue());
        
        // ✅ If AuthUser doesn't exist, check if contact belongs to user with password
        if (authUserOpt.isEmpty()) {
            Optional<UserDto> userOpt = userFacade.findByContactValue(request.getContactValue());
            if (userOpt.isPresent()) {
                UserDto user = userOpt.get();
                
                // ✅ Set tenant context for user's tenant before querying contacts
                UUID originalTenantId = TenantContext.getCurrentTenantIdOrNull();
                try {
                    TenantContext.setCurrentTenantId(user.getTenantId());
                    
                    // ✅ Batch check: Check if user has any AuthUser (password exists) in one query
                    List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts = 
                        userContactService.getUserContacts(user.getId());
                    
                    List<UUID> userContactIds = userContacts.stream()
                        .map(com.fabricmanagement.common.platform.communication.domain.UserContact::getContactId)
                        .toList();
                    
                    boolean userHasPassword = !userContactIds.isEmpty() && 
                        !authUserRepository.findContactIdsByContactIds(userContactIds).isEmpty();
                    
                    if (userHasPassword) {
                        // User has password but this contact is not verified
                        com.fabricmanagement.common.platform.communication.domain.Contact contact = 
                            contactService.findByValue(request.getContactValue())
                                .orElse(null);
                        
                        if (contact != null && !Boolean.TRUE.equals(contact.getIsVerified())) {
                            // ✅ Contact not verified - send verification code
                            log.info("Contact not verified, sending verification code: contactValue={}", 
                                PiiMaskingUtil.maskEmail(request.getContactValue()));
                            
                            try {
                                // Determine verification type based on contact type
                                VerificationType verificationType = contact.getContactType() != null && 
                                    contact.getContactType().isMobile() 
                                    ? VerificationType.PHONE_VERIFICATION 
                                    : VerificationType.EMAIL_VERIFICATION;
                                
                                IssuedVerificationCode issuedCode = verificationCodeManager.issueCode(
                                    request.getContactValue(),
                                    verificationType
                                );
                                verificationService.sendVerificationCode(request.getContactValue(), issuedCode.code());
                                throw new IllegalArgumentException(
                                    "Contact not verified. Verification code sent to " + 
                                    PiiMaskingUtil.maskEmail(request.getContactValue()) + 
                                    ". Please verify your contact first."
                                );
                            } catch (IllegalArgumentException | IllegalStateException ex) {
                                log.warn("Verification code issuance failed: contactValue={}, reason={}",
                                    PiiMaskingUtil.maskEmail(request.getContactValue()), ex.getMessage());
                                throw new IllegalArgumentException(
                                    "Contact not verified. " + ex.getMessage()
                                );
                            }
                        }
                    }
                } finally {
                    // Restore original tenant context
                    if (originalTenantId != null) {
                        TenantContext.setCurrentTenantId(originalTenantId);
                    } else {
                        TenantContext.clear();
                    }
                }
            }
            
            // Contact doesn't exist or user doesn't have password
            throw createContextAwareNotFoundException(request.getContactValue());
        }
        
        AuthUser authUser = authUserOpt.get();

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

        // Get User entity with contacts/departments loaded for JWT generation
        com.fabricmanagement.common.platform.user.domain.User userEntity = 
            userRepository.findByTenantIdAndId(user.getTenantId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

        String accessToken = jwtService.generateAccessToken(userEntity);
        String refreshToken = jwtService.generateRefreshToken(userEntity);

        RefreshToken refreshTokenEntity = RefreshToken.create(
            user.getId(),
            refreshToken,
            Instant.now().plusMillis(refreshTokenExpiration)
        );
        refreshTokenRepository.save(refreshTokenEntity);

        authUser.recordSuccessfulLogin();
        authUserRepository.save(authUser);

        // Get contact value from Contact entity
        String contactValue = userEntity.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(request.getContactValue());

        eventPublisher.publish(new UserLoginEvent(
            user.getTenantId(),
            user.getId(),
            contactValue,
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
    /**
     * Create context-aware "user not found" exception.
     * 
     * <p>Attempts to provide helpful error messages based on domain analysis:
     * <ul>
     *   <li>If domain exists in system: Try to find company and provide company-specific message</li>
     *   <li>Otherwise: Generic signup message</li>
     * </ul>
     * 
     * <p><b>Note:</b> This method does NOT create incorrect email patterns.
     * It only checks if the domain exists in the system to provide better context.
     */
    private IllegalArgumentException createContextAwareNotFoundException(String contactValue) {
        String domain = extractEmailDomain(contactValue);
        if (domain == null) {
            return new IllegalArgumentException("User not found. Please check your credentials.");
        }

        // Check if any contact exists with this domain (for context-aware error messages)
        boolean domainExists = contactService.existsByEmailDomain(domain);
        
        if (domainExists) {
            // Domain exists in system - try to find a user with this domain to get company context
            // We'll search for any user contact with this domain pattern
            // Note: We don't create incorrect emails, we just check domain existence
            
            // Try to find any user with contacts in this domain
            // This helps provide better error messages without creating false email patterns
            Optional<UserDto> anyUserWithDomain = findAnyUserWithDomain(domain);
            
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
        }

        return new IllegalArgumentException(
            "User not found. If you're a new customer, please sign up at fabricmanagement.com"
        );
    }

    /**
     * Find any user with contacts in the given domain.
     * Uses domain pattern matching without creating incorrect email addresses.
     * 
     * <p><b>Note:</b> This method does NOT create incorrect email patterns.
     * It uses LIKE query with domain pattern to find users with contacts in this domain.</p>
     * 
     * @param domain Email domain (e.g., "gmail.com")
     * @return Optional user if found (converted to UserDto)
     */
    private Optional<UserDto> findAnyUserWithDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        
        String normalizedDomain = domain.trim().toLowerCase();
        log.trace("Finding any user with domain: {}", normalizedDomain);
        
        return userRepository.findAnyByEmailDomain(normalizedDomain)
            .map(UserDto::from);
    }

    /**
     * Extract domain from email address.
     * 
     * @param email Email address
     * @return Domain part (e.g., "gmail.com") or null if invalid
     */
    private String extractEmailDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String[] parts = email.split("@");
        if (parts.length != 2 || parts[1].isBlank()) {
            return null;
        }
        return parts[1].trim().toLowerCase();
    }

}

