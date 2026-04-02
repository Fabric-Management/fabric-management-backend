package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeder for initial employees and users. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final OrganizationService organizationService;
  private final UserCreationService userCreationService;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final DepartmentRepository departmentRepository;
  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactionTemplate;

  private static final String DEFAULT_PASSWORD = "password123";

  /** All expected user emails — used for both seeding and granular isSeeded() verification. */
  private static final List<String> EXPECTED_EMAILS =
      List.of(
          "admin@akkaylar.com",
          "production@akkaylar.com",
          "procurement@akkaylar.com",
          "sales@akkaylar.com",
          "warehouse@akkaylar.com",
          "quality@akkaylar.com",
          "hr@akkaylar.com",
          "finance@akkaylar.com");

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
          // Granular check: verify ALL 8 expected users exist, not just admin
          return EXPECTED_EMAILS.stream()
              .allMatch(email -> userRepository.existsByTenantIdAndContactValue(tenantId, email));
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
                OrganizationDto rootOrg =
                    organizationService
                        .getRootOrganization()
                        .orElseThrow(() -> new IllegalStateException("Root organization missing"));

                seedUser(
                    "Ahmet",
                    "Akkay",
                    "admin@akkaylar.com",
                    "ADMIN",
                    "Management",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Mehmet",
                    "Demir",
                    "production@akkaylar.com",
                    "PRODUCTION_MANAGER",
                    "Üretim",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Zeynep",
                    "Kaya",
                    "procurement@akkaylar.com",
                    "PROCUREMENT_SPECIALIST",
                    "Finans ve Satınalma",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Ayşe",
                    "Yılmaz",
                    "sales@akkaylar.com",
                    "SALES_REP",
                    "Satış",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Ali",
                    "Veli",
                    "warehouse@akkaylar.com",
                    "INVENTORY_MANAGER",
                    "Depo ve Lojistik",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Fatma",
                    "Şahin",
                    "quality@akkaylar.com",
                    "QUALITY_INSPECTOR",
                    "Kalite",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Caner",
                    "Yıldız",
                    "hr@akkaylar.com",
                    "HR_MANAGER",
                    "Human Resources",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Veli",
                    "Can",
                    "finance@akkaylar.com",
                    "FINANCE_CONTROLLER",
                    "Finans ve Satınalma",
                    tenant.getId(),
                    rootOrg.getId());
              });
        });
  }

  private void seedUser(
      String firstName,
      String lastName,
      String email,
      String roleCode,
      String departmentName,
      UUID tenantId,
      UUID orgId) {
    if (userRepository.existsByTenantIdAndContactValue(tenantId, email)) {
      return;
    }

    // 1. Resolve Role
    Role role =
        roleService
            .findByCode(roleCode)
            .orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));

    // 2. Resolve Department
    UUID departmentId = null;
    if (departmentName != null) {
      Optional<Department> deptOpt =
          departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentName(
              tenantId, orgId, departmentName);
      if (deptOpt.isPresent()) {
        departmentId = deptOpt.get().getId();
      }
    }

    CreateInternalUserRequest req =
        CreateInternalUserRequest.builder()
            .firstName(firstName)
            .lastName(lastName)
            .contactValue(email)
            .contactType(ContactType.EMAIL)
            .organizationId(orgId)
            .departmentId(departmentId)
            .roleId(role.getId())
            .build();

    UserDto user = userCreationService.createInternalUser(req);

    // 3. Setup Password System ByPass
    setupAuthUser(user.getId(), tenantId);

    log.info("Created user: {} - {} - Role: {}", firstName, email, roleCode);
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
    return 30;
  }
}
