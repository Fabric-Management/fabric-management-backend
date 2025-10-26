package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.domain.Subscription;
import com.fabricmanagement.common.platform.company.domain.SubscriptionStatus;
import com.fabricmanagement.common.platform.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import com.fabricmanagement.common.platform.company.infra.repository.SubscriptionRepository;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import com.fabricmanagement.common.platform.user.domain.ContactType;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant Onboarding Service - Creates new tenant companies with admin users.
 *
 * <p><b>CRITICAL:</b> This service handles the SPECIAL case where tenant_id = company_id</p>
 *
 * <h2>Used in two flows:</h2>
 * <ul>
 *   <li><b>Sales-led:</b> Internal sales team creates tenant (admin endpoint)</li>
 *   <li><b>Self-service:</b> Public user signs up (public endpoint)</li>
 * </ul>
 *
 * <h2>What it creates:</h2>
 * <ol>
 *   <li>Company (with tenant_id = company_id)</li>
 *   <li>Admin User (pre-approved)</li>
 *   <li>Selected OS Subscriptions (trial)</li>
 *   <li>Registration Token (for password setup)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RegistrationTokenRepository tokenRepository;
    private final DomainEventPublisher eventPublisher;
    private final NotificationService notificationService;

    /**
     * Create new tenant via sales-led process.
     *
     * <p><b>CRITICAL:</b> Sets tenant_id = company_id for root tenant</p>
     *
     * @param request Tenant onboarding request
     * @return Onboarding response with token
     */
    @Transactional
    public TenantOnboardingResponse createSalesLedTenant(TenantOnboardingRequest request) {
        log.info("Creating sales-led tenant: company={}", request.getCompanyName());

        validateTenantCreation(request.getTaxId(), request.getAdminContact());

        Company company = createTenantCompany(
            request.getCompanyName(),
            request.getTaxId(),
            request.getCompanyType(),
            request.getAddress(),
            request.getCity(),
            request.getCountry(),
            request.getPhoneNumber(),
            request.getCompanyEmail()
        );

        User adminUser = createAdminUser(
            company.getId(),
            company.getTenantId(),
            request.getAdminFirstName(),
            request.getAdminLastName(),
            request.getAdminContact(),
            request.getAdminDepartment()
        );

        List<Subscription> subscriptions = createInitialSubscriptions(
            company.getId(),
            company.getTenantId(),
            request.getSelectedOS(),
            request.getTrialDays()
        );

        RegistrationToken token = createRegistrationToken(
            request.getAdminContact(),
            RegistrationTokenType.SALES_LED,
            adminUser.getId(),
            company.getId()
        );

        sendWelcomeEmail(
            request.getAdminContact(),
            request.getAdminFirstName(),
            company.getCompanyName(),
            token.getToken(),
            subscriptions
        );

        log.info("✅ Sales-led tenant created - company: {}, admin: {}, token: {}",
            company.getUid(),
            PiiMaskingUtil.maskEmail(adminUser.getContactValue()),
            token.getToken());

        return TenantOnboardingResponse.builder()
            .companyId(company.getId())
            .tenantId(company.getTenantId())
            .companyUid(company.getUid())
            .companyName(company.getCompanyName())
            .adminUserId(adminUser.getId())
            .adminContactValue(adminUser.getContactValue())
            .registrationToken(token.getToken())
            .subscriptions(subscriptions.stream().map(s -> s.getOsCode()).toList())
            .trialEndsAt(subscriptions.get(0).getTrialEndsAt())
            .setupUrl(generateSetupUrl(token.getToken()))
            .build();
    }

    /**
     * Create new tenant via self-service process.
     *
     * <p><b>CRITICAL:</b> Sets tenant_id = company_id for root tenant</p>
     *
     * @param request Self signup request
     * @return Onboarding response with token
     */
    @Transactional
    public TenantOnboardingResponse createSelfServiceTenant(SelfSignupRequest request) {
        log.info("Creating self-service tenant: company={}", request.getCompanyName());

        validateTenantCreation(request.getTaxId(), request.getEmail());

        if (!request.getAcceptedTerms()) {
            throw new IllegalArgumentException("Terms and conditions must be accepted");
        }

        Company company = createTenantCompany(
            request.getCompanyName(),
            request.getTaxId(),
            request.getCompanyType(),
            null, null, null, null, null
        );

        User adminUser = createAdminUser(
            company.getId(),
            company.getTenantId(),
            request.getFirstName(),
            request.getLastName(),
            request.getEmail(),
            null
        );

        List<String> selectedOS = request.getSelectedOS() != null
            ? request.getSelectedOS()
            : List.of();

        List<Subscription> subscriptions = createInitialSubscriptions(
            company.getId(),
            company.getTenantId(),
            selectedOS,
            14  // Self-service: shorter trial (14 days)
        );

        RegistrationToken token = createRegistrationToken(
            request.getEmail(),
            RegistrationTokenType.SELF_SERVICE,
            adminUser.getId(),
            company.getId()
        );

        log.info("✅ Self-service tenant created - company: {}, admin: {}, token: {}",
            company.getUid(),
            PiiMaskingUtil.maskEmail(adminUser.getContactValue()),
            token.getToken());

        return TenantOnboardingResponse.builder()
            .companyId(company.getId())
            .tenantId(company.getTenantId())
            .companyUid(company.getUid())
            .companyName(company.getCompanyName())
            .adminUserId(adminUser.getId())
            .adminContactValue(adminUser.getContactValue())
            .registrationToken(token.getToken())
            .subscriptions(subscriptions.stream().map(s -> s.getOsCode()).toList())
            .trialEndsAt(subscriptions.get(0).getTrialEndsAt())
            .setupUrl(generateSetupUrl(token.getToken()))
            .build();
    }

    /**
     * Create tenant company with SPECIAL tenant_id = company_id logic.
     *
     * <p><b>CRITICAL:</b> This is the ONLY place where tenant_id equals company_id</p>
     */
    private Company createTenantCompany(String name, String taxId, CompanyType type,
                                       String address, String city, String country,
                                       String phone, String email) {
        log.debug("Creating tenant company: name={}, type={}", name, type);

        if (!type.isTenant()) {
            throw new IllegalArgumentException("Company type must be a tenant type");
        }

        Company company = Company.create(name, taxId, type);
        company.setAddress(address);
        company.setCity(city);
        company.setCountry(country);
        company.setPhoneNumber(phone);
        company.setEmail(email);

        // ⚠️ CRITICAL: Save first to get company ID
        Company saved = companyRepository.save(company);

        // ⚠️ CRITICAL: Set tenant_id = company_id for ROOT tenant
        // Company has @AttributeOverride(updatable=true) for this special case
        saved.setTenantId(saved.getId());
        Company finalCompany = companyRepository.save(saved);

        eventPublisher.publish(new CompanyCreatedEvent(
            finalCompany.getTenantId(),
            finalCompany.getId(),
            finalCompany.getCompanyName(),
            finalCompany.getCompanyType().name()
        ));

        log.info("✅ Tenant company created - id: {}, tenant_id: {}, uid: {}",
            finalCompany.getId(),
            finalCompany.getTenantId(),
            finalCompany.getUid());

        return finalCompany;
    }

    /**
     * Create admin user for tenant.
     *
     * <p>User is created within tenant context.</p>
     */
    private User createAdminUser(UUID companyId, UUID tenantId,
                                String firstName, String lastName,
                                String contactValue, String department) {
        log.debug("Creating admin user: contact={}",
            PiiMaskingUtil.maskEmail(contactValue));

        return TenantContext.executeInTenantContext(tenantId, () -> {
            User user = User.create(
                firstName,
                lastName,
                contactValue,
                ContactType.EMAIL,
                companyId,
                department != null ? department : "management"
            );

            User saved = userRepository.save(user);

            eventPublisher.publish(new UserCreatedEvent(
                saved.getTenantId(),
                saved.getId(),
                saved.getDisplayName(),
                saved.getContactValue(),
                saved.getCompanyId()
            ));

            log.info("✅ Admin user created - id: {}, uid: {}",
                saved.getId(), saved.getUid());

            return saved;
        });
    }

    /**
     * Create initial OS subscriptions for tenant.
     *
     * <p>Simple model: Only creates selected OS subscriptions.</p>
     * <p>No mandatory OS - tenant chooses what they need.</p>
     */
    private List<Subscription> createInitialSubscriptions(UUID companyId, UUID tenantId,
                                                         List<String> selectedOS,
                                                         int trialDays) {
        log.debug("Creating initial subscriptions - trialDays: {}", trialDays);

        return TenantContext.executeInTenantContext(tenantId, () -> {
            List<Subscription> subscriptions = new ArrayList<>();

            // Create only selected OS subscriptions (no mandatory OS)
            if (selectedOS != null && !selectedOS.isEmpty()) {
                for (String osCode : selectedOS) {
                    log.debug("Creating subscription: osCode={}", osCode);
                    
                    Subscription sub = Subscription.builder()
                        .osCode(osCode)
                        .osName(getOsName(osCode))
                        .status(SubscriptionStatus.TRIAL)
                        .startDate(Instant.now())
                        .trialEndsAt(Instant.now().plus(trialDays, ChronoUnit.DAYS))
                        .features(Map.of())
                        .build();
                    sub.setTenantId(tenantId);
                    subscriptions.add(subscriptionRepository.save(sub));
                }
            }

            log.info("✅ Created {} subscriptions: {}", 
                subscriptions.size(),
                subscriptions.stream().map(Subscription::getOsCode).toList());
            return subscriptions;
        });
    }

    /**
     * Create registration token for password setup.
     */
    private RegistrationToken createRegistrationToken(String contactValue,
                                                     RegistrationTokenType tokenType,
                                                     UUID userId, UUID companyId) {
        RegistrationToken token = RegistrationToken.create(contactValue, tokenType);
        token.linkTo(userId, companyId);

        return tokenRepository.save(token);
    }

    /**
     * Validate tenant creation preconditions.
     */
    private void validateTenantCreation(String taxId, String contactValue) {
        if (companyRepository.existsByTaxId(taxId)) {
            throw new IllegalArgumentException("Company with this tax ID already exists");
        }

        if (userRepository.existsByContactValue(contactValue)) {
            throw new IllegalArgumentException("Contact value already registered");
        }
    }

    /**
     * Generate setup URL for email.
     */
    private String generateSetupUrl(String token) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null) {
            baseUrl = "http://localhost:3000";
        }
        return baseUrl + "/setup?token=" + token;
    }

    /**
     * Send welcome email with registration link.
     */
    private void sendWelcomeEmail(String email, String firstName, String companyName,
                                 String token, List<Subscription> subscriptions) {
        String setupUrl = generateSetupUrl(token);
        String osList = subscriptions.stream()
            .map(s -> "<li style='margin: 10px 0; color: #374151;'>" + s.getOsCode() + "</li>")
            .reduce((a, b) -> a + b)
            .orElse("<li>None</li>");

        String subject = "Welcome to FabricOS";
        
        String message = String.format("""
            <p style="font-size: 18px; color: #1f2937; font-weight: 600; margin: 0 0 20px 0;">Hello %s! 👋</p>
            
            <p style="color: #4b5563; margin: 0 0 10px 0;">Welcome to <strong>FabricOS</strong>!</p>
            <p style="color: #4b5563; margin: 0 0 30px 0;">Your account for <strong>%s</strong> has been created successfully.</p>
            
            <div style="background: #f9fafb; padding: 24px; border-radius: 6px; border: 1px solid #e5e7eb; margin: 0 0 24px 0;">
                <p style="margin: 0 0 12px 0; font-weight: 600; color: #1f2937; font-size: 15px;">Your Active Subscriptions</p>
                <ul style="margin: 0; padding-left: 20px; list-style: none;">
                    %s
                </ul>
            </div>
            
            <div style="background: #f0fdf4; border: 1px solid #86efac; padding: 16px; border-radius: 6px; margin: 0 0 30px 0;">
                <p style="margin: 0; color: #166534; font-size: 14px;">🎁 <strong>Trial Period:</strong> 90 days FREE</p>
            </div>
            
            <div style="text-align: center; margin: 30px 0;">
                <a href="%s" style="display: inline-block; background: #667eea; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 15px;">
                    Complete Registration →
                </a>
            </div>
            
            <p style="font-size: 13px; color: #9ca3af; text-align: center; margin: 20px 0 0 0;">
                This link will expire in 24 hours
            </p>
            
            <div style="height: 1px; background: #e5e7eb; margin: 30px 0;"></div>
            
            <p style="font-size: 13px; color: #6b7280; margin: 0;">
                Need help? Contact <a href="mailto:support@fabricmanagement.com" style="color: #667eea; text-decoration: none;">support@fabricmanagement.com</a>
            </p>
            """, firstName, companyName, osList, setupUrl);

        notificationService.sendNotification(email, subject, message);
    }

    /**
     * Get OS display name.
     */
    private String getOsName(String osCode) {
        return switch (osCode) {
            case "FabricOS" -> "Fabric Management Base Platform";
            case "YarnOS" -> "Yarn Production OS";
            case "LoomOS" -> "Weaving Production OS";
            case "KnitOS" -> "Knitting Production OS";
            case "DyeOS" -> "Dyeing & Finishing OS";
            case "AnalyticsOS" -> "Analytics & Reporting OS";
            case "IntelligenceOS" -> "AI & Intelligence OS";
            case "EdgeOS" -> "IoT & Edge Computing OS";
            case "AccountOS" -> "Accounting OS";
            case "CustomOS" -> "Custom Integration OS";
            default -> osCode;
        };
    }
}

