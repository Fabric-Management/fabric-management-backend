package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeder for partner/external users. Runs AFTER TradingPartnerSeeder (order=40) to ensure partner
 * organizations exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerUserSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final OrganizationRepository organizationRepository;
  private final UserCreationService userCreationService;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactionTemplate;
  private final ContactRepository contactRepository;

  private static final String DEFAULT_PASSWORD = "password123";

  private record SeedExternalUserProfile(
      String firstName, String lastName, String email, String roleCode, String organizationName) {}

  private static final List<SeedExternalUserProfile> EXTERNAL_PROFILES =
      List.of(
          // ── 10. EXTERNAL — Supplier (Oz Cotton Yarns Inc.) ──
          new SeedExternalUserProfile(
              "Oscar", "Ozkan", "oscar@ozcotton.com", "PARTNER_OWNER", "Oz Cotton Yarns Inc."),
          new SeedExternalUserProfile(
              "Aylin",
              "Accounts",
              "accounting@ozcotton.com",
              "PARTNER_ACCOUNTANT",
              "Oz Cotton Yarns Inc."),

          // ── 11. EXTERNAL — Customer (Global Fashion Wear Corp.) ──
          new SeedExternalUserProfile(
              "George",
              "Global",
              "buyer@globalfashion.com",
              "PARTNER_BUYER",
              "Global Fashion Wear Corp."),
          new SeedExternalUserProfile(
              "Fatma",
              "Fashion",
              "merchandiser@globalfashion.com",
              "PARTNER_VIEWER",
              "Global Fashion Wear Corp."),

          // ── 12. EXTERNAL — Subcontractor / Fason (South Dyeing Facilities) ──
          new SeedExternalUserProfile(
              "Selim",
              "South",
              "manager@southdyeing.com",
              "PARTNER_OWNER",
              "South Dyeing Facilities"),
          new SeedExternalUserProfile(
              "Deniz",
              "Dispatch",
              "logistics@southdyeing.com",
              "PARTNER_VIEWER",
              "South Dyeing Facilities"),

          // ── 13. EXTERNAL — Service Provider (Fast Logistics Shipping LLC) ──
          new SeedExternalUserProfile(
              "Logan",
              "Logistics",
              "ops@fastlogistics.com",
              "PARTNER_VIEWER",
              "Fast Logistics Shipping LLC"),

          // ── 14. EXTERNAL — Both / Hem Tedarikçi Hem Müşteri (Central Textile Mills Ltd.) ──
          new SeedExternalUserProfile(
              "Cenk",
              "Central",
              "trade@centraltextile.com",
              "PARTNER_OWNER",
              "Central Textile Mills Ltd."),
          new SeedExternalUserProfile(
              "Burcu",
              "Books",
              "finance@centraltextile.com",
              "PARTNER_ACCOUNTANT",
              "Central Textile Mills Ltd."));

  private static final String LAST_SEEDED_EMAIL = "finance@centraltextile.com";

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> tenantOpt = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (tenantOpt.isEmpty()) {
      return false;
    }

    return TenantContext.executeInTenantContext(
        tenantOpt.get().getId(),
        () -> {
          UUID tenantId = tenantOpt.get().getId();
          return userRepository.existsByTenantIdAndContactValue(tenantId, LAST_SEEDED_EMAIL);
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(() -> new IllegalStateException("Tenant must be seeded before Users"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                Map<String, UUID> orgNameToId =
                    organizationRepository.findByTenantIdAndIsActiveTrue(tenant.getId()).stream()
                        .collect(
                            Collectors.toMap(
                                Organization::getName, Organization::getId, (a, b) -> a));

                int seededCount = 0;
                for (SeedExternalUserProfile profile : EXTERNAL_PROFILES) {
                  if (seedExternalUser(profile, tenant.getId(), orgNameToId)) {
                    seededCount++;
                  }
                }
                log.info(
                    "Seeded {}/{} external playground users for tenant: {}",
                    seededCount,
                    EXTERNAL_PROFILES.size(),
                    tenant.getId());
              });
        });
  }

  private boolean seedExternalUser(
      SeedExternalUserProfile profile, UUID tenantId, Map<String, UUID> orgNameToId) {
    if (userRepository.existsByTenantIdAndContactValue(tenantId, profile.email())) {
      return false;
    }

    UUID partnerOrgId = orgNameToId.get(profile.organizationName());
    if (partnerOrgId == null) {
      log.warn(
          "Partner org not found for external user: {}. Organization '{}' must be seeded first.",
          profile.email(),
          profile.organizationName());
      return false;
    }

    Role role =
        roleService
            .findByCode(profile.roleCode())
            .orElseThrow(() -> new IllegalStateException("Role not found: " + profile.roleCode()));

    CreateExternalUserRequest req =
        CreateExternalUserRequest.builder()
            .firstName(profile.firstName())
            .lastName(profile.lastName())
            .contactValue(profile.email())
            .contactType(ContactType.EMAIL)
            .organizationId(partnerOrgId)
            .suppressEmailInvitation(true)
            .build();

    UserDto user = userCreationService.createExternalUser(req);

    // Override role (createExternalUser assigns EXTERNAL_USER by default)
    User entity =
        userRepository
            .findById(user.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException("User just created but not found: " + user.getId()));
    entity.setRole(role);
    userRepository.save(entity);

    setupAuthUser(user.getId(), tenantId);

    // Mark contact as verified
    contactRepository
        .findByTenantIdAndContactValue(tenantId, profile.email())
        .ifPresent(
            contact -> {
              contact.verify();
              contactRepository.save(contact);
            });

    log.debug(
        "Created external user: {} {} — {} ({}) linked to {}",
        profile.firstName(),
        profile.lastName(),
        profile.email(),
        profile.roleCode(),
        profile.organizationName());
    return true;
  }

  private void setupAuthUser(UUID userId, UUID tenantId) {
    if (!authUserRepository.existsByUserId(userId)) {
      AuthUser authUser = AuthUser.create(userId, passwordEncoder.encode(DEFAULT_PASSWORD));
      authUser.setTenantId(tenantId);
      authUser.verify();
      authUserRepository.save(authUser);
    }
  }

  @Override
  public int getOrder() {
    return 45; // Must be after TradingPartnerSeeder (40)
  }
}
