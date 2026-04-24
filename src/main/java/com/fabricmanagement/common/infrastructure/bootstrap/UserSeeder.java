package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
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
  private final ContactRepository contactRepository;

  private static final String DEFAULT_PASSWORD = "password123";

  /** All expected user emails — used for both seeding and granular isSeeded() verification. */
  private static final List<String> EXPECTED_EMAILS =
      List.of(
          "admin@akkayalar.com",
          "production@akkayalar.com",
          "procurement@akkayalar.com",
          "sales@akkayalar.com",
          "warehouse@akkayalar.com",
          "quality@akkayalar.com",
          "hr@akkayalar.com",
          "finance@akkayalar.com",
          "marketing@akkayalar.com",
          "admin@fabricos.io");

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
                    "Admin",
                    "Akkayalar",
                    "admin@akkayalar.com",
                    "ADMIN",
                    null, // ADMIN role has cross-org access; no single dept needed
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Production",
                    "Manager",
                    "production@akkayalar.com",
                    "MANAGER",
                    "Yarn Production",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Procurement",
                    "Specialist",
                    "procurement@akkayalar.com",
                    "WORKER",
                    "Procurement",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Sales",
                    "Executive",
                    "sales@akkayalar.com",
                    "WORKER",
                    "Sales & Marketing",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Logistics",
                    "Manager",
                    "warehouse@akkayalar.com",
                    "MANAGER",
                    "Warehouse & Logistics",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Quality",
                    "Controller",
                    "quality@akkayalar.com",
                    "WORKER",
                    "Quality Control",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "HR",
                    "Manager",
                    "hr@akkayalar.com",
                    "MANAGER",
                    "Human Resources",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Finance",
                    "Director",
                    "finance@akkayalar.com",
                    "MANAGER",
                    "Finance & Accounting",
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "FabricOS",
                    "Admin",
                    "admin@fabricos.io",
                    "PLATFORM_ADMIN",
                    null, // Platform admins operate globally, no specific department
                    tenant.getId(),
                    rootOrg.getId());
                seedUser(
                    "Marketing",
                    "Test",
                    "marketing@akkayalar.com",
                    "WORKER",
                    "Sales & Marketing",
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

    // 4. Mark all auto-assigned contacts as verified to allow login
    // Bypass L1 cache issues by directly checking the contact repository instead of
    // user.getUserContacts()
    contactRepository
        .findByTenantIdAndContactValue(tenantId, email)
        .ifPresent(
            contact -> {
              contact.verify();
              contactRepository.save(contact);
            });

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
