package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
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
import com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.CompanyContactService;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.CompanyAddressService;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.company.app.TenantSeedService;
import com.fabricmanagement.common.platform.user.app.UserDepartmentService;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.platform.user.infra.repository.RoleRepository;
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
import java.util.Optional;
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
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final ContactService contactService;
    private final UserContactService userContactService;
    private final FrontendUrlProvider frontendUrlProvider;
    private final CompanyContactService companyContactService;
    private final AddressService addressService;
    private final CompanyAddressService companyAddressService;
    private final DepartmentRepository departmentRepository;
    private final UserDepartmentService userDepartmentService;
    private final RoleRepository roleRepository;
    private final TenantSeedService tenantSeedService;

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
            request.getCompanyType()
        );
        
        // Add company address and contact if provided (new communication system)
        addCompanyAddressAndContact(company.getId(), company.getTenantId(), request);

        User adminUser = createAdminUser(
            company.getId(),
            company.getTenantId(),
            request.getAdminFirstName(),
            request.getAdminLastName(),
            request.getAdminContact(),
            request.getAdminDepartment()
        );

        // Get selected OS from request, or default to base platform (FabricOS)
        // This ensures every tenant has at least base platform subscription
        List<String> selectedOS = request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"); // Default: At least base platform for sales-led onboarding

        List<Subscription> subscriptions = createInitialSubscriptions(
            company.getId(),
            company.getTenantId(),
            selectedOS,
            request.getTrialDays()
        );

        RegistrationToken token = createRegistrationToken(
            request.getAdminContact(),
            RegistrationTokenType.SALES_LED,
            adminUser.getId(),
            company.getId()
        );

        // Seed default departments and positions for new tenant
        tenantSeedService.seedDepartmentsAndPositions(company.getTenantId(), company.getId());

        // Send welcome email AFTER transaction commit (use sync to ensure email is queued)
        sendWelcomeEmail(
            request.getAdminContact(),
            request.getAdminFirstName(),
            company.getCompanyName(),
            token.getToken(),
            subscriptions
        );

        // Get admin contact value from Contact entity
        String adminContact = adminUser.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(request.getAdminContact());

        log.info("✅ Sales-led tenant created - company: {}, admin: {}, token: {}",
            company.getUid(),
            PiiMaskingUtil.maskEmail(adminContact),
            token.getToken());

        return TenantOnboardingResponse.builder()
            .companyId(company.getId())
            .tenantId(company.getTenantId())
            .companyUid(company.getUid())
            .companyName(company.getCompanyName())
            .adminUserId(adminUser.getId())
            .adminContactValue(adminContact)
            .registrationToken(token.getToken())
            .subscriptions(subscriptions.stream().map(s -> s.getOsCode()).toList())
            .trialEndsAt(subscriptions.isEmpty() ? null : subscriptions.get(0).getTrialEndsAt())
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
            request.getCompanyType()
        );

        User adminUser = createAdminUser(
            company.getId(),
            company.getTenantId(),
            request.getFirstName(),
            request.getLastName(),
            request.getEmail(),
            null
        );

        // Get selected OS from request, or default to base platform (FabricOS)
        List<String> selectedOS = request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"); // Default: At least base platform for self-service signup

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

        // Seed default departments and positions for new tenant
        tenantSeedService.seedDepartmentsAndPositions(company.getTenantId(), company.getId());

        // Self-service signup: No verification code needed
        // Email link click = email verified (user registered with their own email)
        // Verification codes are only needed for unverified contacts during login

        // Get admin contact value from Contact entity
        String adminContact = adminUser.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(request.getEmail());

        log.info("✅ Self-service tenant created - company: {}, admin: {}, token: {}",
            company.getUid(),
            PiiMaskingUtil.maskEmail(adminContact),
            token.getToken());

        return TenantOnboardingResponse.builder()
            .companyId(company.getId())
            .tenantId(company.getTenantId())
            .companyUid(company.getUid())
            .companyName(company.getCompanyName())
            .adminUserId(adminUser.getId())
            .adminContactValue(adminContact)
            .registrationToken(token.getToken())
            .subscriptions(subscriptions.stream().map(s -> s.getOsCode()).toList())
            .trialEndsAt(subscriptions.isEmpty() ? null : subscriptions.get(0).getTrialEndsAt())
            .setupUrl(generateSetupUrl(token.getToken()))
            .build();
    }

    /**
     * Add company address and contact if provided in request.
     * 
     * <p>Creates Address and Contact entities, then assigns them to company via junction tables.</p>
     */
    private void addCompanyAddressAndContact(UUID companyId, UUID tenantId,
                                            TenantOnboardingRequest request) {
        addCompanyAddressAndContact(companyId, tenantId,
            request.getAddress(), request.getCity(), request.getCountry(),
            request.getPhoneNumber(), request.getCompanyEmail());
    }

    /**
     * Add company address and contact if provided.
     */
    private void addCompanyAddressAndContact(UUID companyId, UUID tenantId,
                                            String address, String city, String country,
                                            String phoneNumber, String email) {
        boolean hasAddressInfo = address != null || city != null || country != null;
        boolean hasContactInfo = phoneNumber != null || email != null;

        if (!hasAddressInfo && !hasContactInfo) {
            return; // Nothing to add
        }

        log.debug("Adding company address/contact: companyId={}, hasAddress={}, hasContact={}",
            companyId, hasAddressInfo, hasContactInfo);

        // Set tenant context for address/contact creation
        UUID originalTenantId = TenantContext.getCurrentTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);

            // Add address if provided
            if (hasAddressInfo) {
                com.fabricmanagement.common.platform.communication.domain.Address addr = 
                    addressService.createAddress(
                        address != null ? address : "",
                        city != null ? city : "",
                        null, // state
                        null, // postalCode
                        country != null ? country : "",
                        AddressType.HEADQUARTERS,
                        "Headquarters"
                    );

                companyAddressService.assignAddress(
                    companyId,
                    addr.getId(),
                    true,  // isPrimary
                    true   // isHeadquarters
                );

                log.info("✅ Company headquarters address added: companyId={}", companyId);
            }

            // Add phone contact if provided
            if (phoneNumber != null) {
                com.fabricmanagement.common.platform.communication.domain.Contact phoneContact = 
                    contactService.createContact(
                        phoneNumber,
                        com.fabricmanagement.common.platform.communication.domain.ContactType.LANDLINE,
                        "Main Phone",
                        false, // isPersonal (company contact)
                        null   // parentContactId
                    );

                companyContactService.assignContact(
                    companyId,
                    phoneContact.getId(),
                    true,  // isDefault
                    null   // department
                );

                log.info("✅ Company phone contact added: companyId={}", companyId);
            }

            // Add email contact if provided
            if (email != null) {
                com.fabricmanagement.common.platform.communication.domain.Contact emailContact = 
                    contactService.createContact(
                        email,
                        com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL,
                        "Main Email",
                        false, // isPersonal (company contact)
                        null   // parentContactId
                    );

                // Only set as default if phone wasn't added
                boolean isDefault = phoneNumber == null;
                companyContactService.assignContact(
                    companyId,
                    emailContact.getId(),
                    isDefault,
                    null   // department
                );

                log.info("✅ Company email contact added: companyId={}", companyId);
            }
        } catch (Exception e) {
            log.warn("Failed to add company address/contact: companyId={}, error={}", 
                companyId, e.getMessage());
            // Continue - address/contact addition is not critical for onboarding
        } finally {
            // Restore original tenant context
            if (originalTenantId != null) {
                TenantContext.setCurrentTenantId(originalTenantId);
            }
        }
    }

    /**
     * Create tenant company with SPECIAL tenant_id = company_id logic.
     *
     * <p><b>CRITICAL:</b> This is the ONLY place where tenant_id equals company_id</p>
     */
    private Company createTenantCompany(String name, String taxId, CompanyType type) {
        log.debug("Creating tenant company: name={}, type={}", name, type);

        if (!type.isTenant()) {
            throw new IllegalArgumentException("Company type must be a tenant type");
        }

        Company company = Company.create(name, taxId, type);

        // ⚠️ CRITICAL: Generate tenant UID from company name BEFORE save
        // Company UID = Tenant UID (no module code, no UUID suffix)
        // Format: {COMPANY_NAME_PREFIX}-{SEQUENCE}
        // Example: "Akkayalar Tekstil" → "AKKAYALAR-001"
        String tenantUid = generateTenantUidFromCompanyName(name);
        company.setUid(tenantUid);
        
        // ⚠️ CRITICAL: Set tenant UID in TenantContext BEFORE save
        // This ensures BaseEntity.onCreate() uses correct tenant UID for other fields
        TenantContext.setCurrentTenantUid(tenantUid);
        log.debug("Tenant UID generated and set: {} for company: {}", tenantUid, name);

        // ⚠️ CRITICAL: Save first to get company ID
        Company saved = companyRepository.save(company);

        // ⚠️ CRITICAL: Set tenant_id = company_id for ROOT tenant
        // Company has @AttributeOverride(updatable=true) for this special case
        saved.setTenantId(saved.getId());
        Company finalCompany = companyRepository.save(saved);

        // Ensure tenant UID is still set (redundant but safe)
        TenantContext.setCurrentTenantUid(finalCompany.getUid());
        log.debug("Tenant UID confirmed: {} for tenantId={}", finalCompany.getUid(), finalCompany.getTenantId());

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
            // Create User (new system - no deprecated fields)
            User user = User.create(
                firstName,
                lastName,
                companyId
            );

            User saved = userRepository.save(user);

            // Create Contact entity and assign to user (new system)
            // Note: For onboarding, contact is pre-verified (user provides their own email)
            com.fabricmanagement.common.platform.communication.domain.Contact contact = 
                contactService.createContact(
                    contactValue,
                    com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL,
                    "Primary",
                    true, // isPersonal
                    null  // parentContactId
                );
            
            // Verify contact for authentication (onboarding = pre-verified)
            contact = contactService.verifyContact(contact.getId());

            // Assign contact to user with authentication flag
            userContactService.assignContact(
                saved.getId(),
                contact.getId(),
                true  // isDefault
            );

            // Assign department if provided (new system)
            if (department != null) {
                // Find department by name (assumes department exists in seed data)
                // Note: Department lookup requires tenantId and companyId
                UUID currentTenantId = TenantContext.getCurrentTenantId();
                departmentRepository.findByTenantIdAndCompanyIdAndDepartmentName(
                    currentTenantId, companyId, department
                ).ifPresent(dept -> {
                    userDepartmentService.assignDepartment(
                        saved.getId(),
                        dept.getId(),
                        true,  // isPrimary
                        TenantContext.getCurrentUserId()
                    );
                });
            }

            // ✅ Assign ADMIN role from platform (system tenant) - TENANT ADMIN ONLY
            // ⚠️ IMPORTANT: This method is ONLY for tenant onboarding admin users.
            // Regular users created by admins will get roles from request (UserService.createInternalUser).
            UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
            Role assignedRole = assignTenantAdminRole(saved, systemTenantId, tenantId);
            
            saved.setRole(assignedRole);
            userRepository.save(saved);
            log.info("✅ Tenant admin role assigned from platform: userId={}, roleCode={}, roleId={}", 
                saved.getId(), assignedRole.getRoleCode(), assignedRole.getId());

            String savedContactValue = contact.getContactValue();
            eventPublisher.publish(new UserCreatedEvent(
                saved.getTenantId(),
                saved.getId(),
                saved.getDisplayName(),
                savedContactValue,
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
     * Generate setup URL for email using configured frontend URL.
     * 
     * <p>Priority: FRONTEND_URL → APP_BASE_URL → localhost fallback (dev only)</p>
     */
    private String generateSetupUrl(String token) {
        return frontendUrlProvider.buildUrl("/setup?token=" + token);
    }

    /**
     * Send welcome email with registration link.
     * 
     * <p>Uses sync method to ensure email is queued before transaction commit.
     * This prevents async timing issues where email might not be queued.</p>
     */
    private void sendWelcomeEmail(String email, String firstName, String companyName,
                                 String token, List<Subscription> subscriptions) {
        String setupUrl = generateSetupUrl(token);
        String osList = subscriptions.stream()
            .map(s -> "<li style='margin: 10px 0; color: #374151;'>" + s.getOsCode() + "</li>")
            .reduce((a, b) -> a + b)
            .orElse("<li style='margin: 10px 0; color: #374151;'>None</li>");

        String subject = "Welcome to FabricOS";
        
        // Smart renderer: Uses frontend templates with backend fallback
        // Frontend templates prioritized for better UX (design system consistency)
        String message = emailTemplateRenderer.renderWelcome(firstName, companyName, osList, setupUrl);

        // Use sync to ensure email is queued in same transaction
        notificationService.sendNotificationSync(email, subject, message);
    }

    /**
     * Assign tenant admin role to the first user created during tenant onboarding.
     * 
     * <p><b>⚠️ IMPORTANT: This method is ONLY for tenant onboarding admin users.</b></p>
     * <p><b>Regular users created by admins will get roles from request via UserService.createInternalUser().</b></p>
     * 
     * <p><b>Role Assignment Strategy:</b></p>
     * <ol>
     *   <li><b>ADMIN role</b> (required for tenant admin) - Primary choice</li>
     *   <li><b>Fallback roles</b> (only if ADMIN missing) - PROD_MANAGER → HR_MANAGER → Any active role</li>
     *   <li><b>Exception</b> if no roles exist (system configuration error)</li>
     * </ol>
     * 
     * <p><b>Why fallback?</b></p>
     * <ul>
     *   <li>✅ Ensures tenant onboarding succeeds even if ADMIN role temporarily missing</li>
     *   <li>✅ User always has a role (security requirement)</li>
     *   <li>✅ Mail sending is guaranteed (user created successfully)</li>
     *   <li>⚠️ Fallback roles are logged as warnings for admin review</li>
     * </ul>
     * 
     * <p><b>Security Note:</b></p>
     * <ul>
     *   <li>✅ Only called during tenant onboarding (createAdminUser)</li>
     *   <li>✅ Regular users get roles from request (UserService handles this)</li>
     *   <li>✅ Admin users cannot assign ADMIN role to others via this method</li>
     * </ul>
     * 
     * @param user User entity to assign role to (tenant admin user)
     * @param systemTenantId System tenant ID (where platform roles are stored)
     * @param tenantId Current tenant ID (for logging)
     * @return Assigned role (never null - throws exception if no roles found)
     * @throws IllegalStateException if no platform roles exist (system configuration error)
     */
    private Role assignTenantAdminRole(User user, UUID systemTenantId, UUID tenantId) {
        // Priority 1: ADMIN role (REQUIRED for tenant admin users)
        Optional<Role> adminRole = roleRepository.findByTenantIdAndRoleCode(systemTenantId, "ADMIN");
        if (adminRole.isPresent()) {
            log.debug("✅ ADMIN role assigned to tenant admin: userId={}, tenantId={}", 
                user.getId(), tenantId);
            return adminRole.get();
        }
        
        log.warn("⚠️ ADMIN role not found in platform. Using fallback role for tenant admin... tenantId={}", tenantId);
        
        // Priority 2: PROD_MANAGER (has management capabilities - acceptable fallback)
        Optional<Role> prodManagerRole = roleRepository.findByTenantIdAndRoleCode(systemTenantId, "PROD_MANAGER");
        if (prodManagerRole.isPresent()) {
            log.warn("⚠️ ADMIN role not found, assigned PROD_MANAGER as fallback for tenant admin: " +
                "userId={}, tenantId={}. Please ensure ADMIN role exists in platform.", 
                user.getId(), tenantId);
            return prodManagerRole.get();
        }
        
        // Priority 3: HR_MANAGER (administrative role - acceptable fallback)
        Optional<Role> hrManagerRole = roleRepository.findByTenantIdAndRoleCode(systemTenantId, "HR_MANAGER");
        if (hrManagerRole.isPresent()) {
            log.warn("⚠️ ADMIN and PROD_MANAGER roles not found, assigned HR_MANAGER as fallback for tenant admin: " +
                "userId={}, tenantId={}. Please ensure ADMIN role exists in platform.", 
                user.getId(), tenantId);
            return hrManagerRole.get();
        }
        
        // Priority 4: Any active role (last resort - not ideal but better than no role)
        List<Role> allRoles = roleRepository.findByTenantIdAndIsActiveTrue(systemTenantId);
        if (!allRoles.isEmpty()) {
            Role fallbackRole = allRoles.get(0);
            log.error("❌ ADMIN role not found, assigned '{}' as fallback for tenant admin: " +
                "userId={}, tenantId={}. This is a configuration issue - ADMIN role should exist in platform.",
                fallbackRole.getRoleCode(), user.getId(), tenantId);
            return fallbackRole;
        }
        
        // ❌ CRITICAL: No roles found - this is a system configuration error
        // Tenant cannot be created without platform roles
        log.error("❌ CRITICAL: No platform roles found in system tenant. " +
            "Migration V013 must be executed before tenant creation. tenantId={}", tenantId);
        throw new IllegalStateException(
            "Platform roles not initialized. Please ensure migration V013 has been executed. " +
            "This is a system configuration error that must be resolved before tenant creation."
        );
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

    /**
     * Generate tenant UID from company name.
     * 
     * <p>Format: {COMPANY_NAME_PREFIX}-{SEQUENCE}
     * Example: "Akkayalar Tekstil Dokuma San.Tic.Ltd.Sti" → "AKKAYALAR-001"
     * 
     * <p>Algorithm:
     * <ol>
     *   <li>Extract first word from company name (uppercase, max 10 chars)</li>
     *   <li>Remove special characters</li>
     *   <li>Add sequence number (001, 002, etc.) - check uniqueness if needed</li>
     * </ol>
     * 
     * @param companyName Company name
     * @return Generated tenant UID
     */
    private String generateTenantUidFromCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "SYS-000"; // Fallback
        }

        // Extract first word (uppercase, max 10 chars, alphanumeric only)
        String[] words = companyName.trim().split("\\s+");
        String firstWord = words[0].toUpperCase()
            .replaceAll("[^A-Z0-9]", "") // Remove special chars
            .substring(0, Math.min(10, words[0].length()));

        if (firstWord.isEmpty()) {
            firstWord = "COMPANY";
        }

        // Generate sequence: Check existing companies with same prefix for uniqueness
        // Uses counter-based approach with uniqueness validation
        // Format: {PREFIX}-{SEQUENCE} where SEQUENCE is 001-999
        int counter = 1;
        String candidateUid;
        do {
            String sequence = String.format("%03d", counter);
            candidateUid = String.format("%s-%s", firstWord, sequence);
            
            // Check if this UID already exists (global uniqueness check)
            boolean exists = companyRepository.findByUid(candidateUid).isPresent();
            if (!exists) {
                break; // Found unique UID
            }
            counter++;
            
            // Safety: Prevent infinite loop (max 999 companies with same prefix)
            if (counter > 999) {
                // Fallback: Use UUID suffix if too many collisions
                // This handles edge case where 999+ companies share same prefix
                String uuidSuffix = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 4)
                    .toUpperCase();
                candidateUid = String.format("%s-%s", firstWord, uuidSuffix);
                log.warn("Too many collisions for prefix {} (>{}, using UUID suffix: {}", 
                    firstWord, 999, candidateUid);
                break;
            }
        } while (counter <= 999);

        log.debug("Generated tenant UID: {} from company name: {}", candidateUid, companyName);
        return candidateUid;
    }
}

