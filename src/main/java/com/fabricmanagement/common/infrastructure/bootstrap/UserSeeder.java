package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
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

/**
 * Seeder for initial employees and users.
 *
 * <p>Generates 68 playground users (2 admins + 66 job-title-specific personas) for the Nexus
 * Fabrics demo tenant. Each user has a thematic name that hints at their role for intuitive
 * simulation UX.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements DataSeeder {

  private final TenantSystemService tenantService;
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

  /**
   * Seed user profile definition. Each entry maps to exactly one user in the playground tenant.
   *
   * @param firstName Thematic first name (hints at role)
   * @param lastName Thematic last name (hints at domain)
   * @param email Unique, code-derived email address
   * @param roleCode ADMIN, MANAGER, SUPERVISOR, WORKER, or PLATFORM_ADMIN
   * @param departmentCode OrganizationSeeder department code (nullable for admins)
   * @param jobTitleCode JobTitleSeedData preset code (nullable for admins)
   */
  private record SeedUserProfile(
      String firstName,
      String lastName,
      String email,
      String roleCode,
      String departmentCode,
      String jobTitleCode) {}

  // ─── Thematic Persona Definitions ───

  /**
   * All 62 seed user profiles. Names are chosen to be memorable and to subtly hint at the user's
   * role in the textile value chain. For example, "Spencer Mills" is the Spinning Mill Manager
   * (Spencer ≈ Spin, Mills = factory), "Dylan Hue" manages the Dyehouse (Dye ≈ Dylan, Hue = color).
   */
  private static final List<SeedUserProfile> ALL_PROFILES =
      List.of(
          // ── System Admins ──
          new SeedUserProfile("Adrian", "Nexus", "admin@nexusfabrics.com", "ADMIN", null, null),
          new SeedUserProfile(
              "Platform", "Admin", "admin@fabricos.io", "PLATFORM_ADMIN", null, null),

          // ── 1. Fiber & Yarn (Spinning) — YARN ──
          new SeedUserProfile(
              "Spencer", "Mills", "spin.mgr@nexusfabrics.com", "MANAGER", "YARN", "SPIN_MGR"),
          new SeedUserProfile(
              "Shane",
              "Spindle",
              "spin.super@nexusfabrics.com",
              "SUPERVISOR",
              "YARN",
              "SPIN_SUPER"),
          new SeedUserProfile(
              "Blake", "Bower", "blow.op@nexusfabrics.com", "WORKER", "YARN", "BLOW_OP"),
          new SeedUserProfile(
              "Carl", "Carder", "card.op@nexusfabrics.com", "WORKER", "YARN", "CARD_OP"),
          new SeedUserProfile(
              "Simon", "Strand", "spin.op@nexusfabrics.com", "WORKER", "YARN", "SPIN_OP"),
          new SeedUserProfile(
              "Wendy", "Winder", "wind.op@nexusfabrics.com", "WORKER", "YARN", "WIND_OP"),

          // ── 2. Fabric Formation — WEAVING / KNITTING ──
          new SeedUserProfile(
              "Walter", "Webb", "weav.mgr@nexusfabrics.com", "MANAGER", "WEAVING", "WEAV_MGR"),
          new SeedUserProfile(
              "Kate", "Knoll", "knit.mgr@nexusfabrics.com", "MANAGER", "KNITTING", "KNIT_MGR"),
          new SeedUserProfile(
              "Wesley",
              "Weaver",
              "weav.super@nexusfabrics.com",
              "SUPERVISOR",
              "WEAVING",
              "WEAV_SUPER"),
          new SeedUserProfile(
              "Kyle",
              "Knitter",
              "knit.super@nexusfabrics.com",
              "SUPERVISOR",
              "KNITTING",
              "KNIT_SUPER"),
          new SeedUserProfile(
              "Warren", "Warp", "warp.op@nexusfabrics.com", "WORKER", "WEAVING", "WARP_OP"),
          new SeedUserProfile(
              "Leo", "Loom", "weaver@nexusfabrics.com", "WORKER", "WEAVING", "WEAVER"),
          new SeedUserProfile(
              "Kai", "Knitley", "knitter@nexusfabrics.com", "WORKER", "KNITTING", "KNITTER"),

          // ── 3. Dyeing & Finishing — DYEING ──
          new SeedUserProfile(
              "Dylan", "Hue", "dye.mgr@nexusfabrics.com", "MANAGER", "DYEING", "DYE_MGR"),
          new SeedUserProfile(
              "Frank", "Finch", "finish.mgr@nexusfabrics.com", "MANAGER", "DYEING", "FINISH_MGR"),
          new SeedUserProfile(
              "Dean", "Dyer", "dye.super@nexusfabrics.com", "SUPERVISOR", "DYEING", "DYE_SUPER"),
          new SeedUserProfile(
              "Charlie", "Chem", "chemist@nexusfabrics.com", "WORKER", "DYEING", "CHEMIST"),
          new SeedUserProfile(
              "Danny", "Mixer", "dye.mixer@nexusfabrics.com", "WORKER", "DYEING", "DYE_MIXER"),
          new SeedUserProfile(
              "Jake", "Jetson", "jet.op@nexusfabrics.com", "WORKER", "DYEING", "JET_OP"),
          new SeedUserProfile(
              "Steve", "Stent", "stenter.op@nexusfabrics.com", "WORKER", "DYEING", "STENTER_OP"),
          new SeedUserProfile(
              "Paul", "Pratt", "print.op@nexusfabrics.com", "WORKER", "DYEING", "PRINT_OP"),

          // ── 4. Garment Manufacturing — GARMENT ──
          new SeedUserProfile(
              "Gary", "Garner", "garm.mgr@nexusfabrics.com", "MANAGER", "GARMENT", "GARM_MGR"),
          new SeedUserProfile(
              "Linda", "Lane", "line.mgr@nexusfabrics.com", "SUPERVISOR", "GARMENT", "LINE_MGR"),
          new SeedUserProfile(
              "Pat", "Palmer", "pattern@nexusfabrics.com", "WORKER", "GARMENT", "PATTERN"),
          new SeedUserProfile(
              "Curtis",
              "Cutter",
              "cut.super@nexusfabrics.com",
              "SUPERVISOR",
              "GARMENT",
              "CUT_SUPER"),
          new SeedUserProfile(
              "Sarah", "Stitch", "sew.op@nexusfabrics.com", "WORKER", "GARMENT", "SEW_OP"),
          new SeedUserProfile(
              "Ian", "Press", "iron.op@nexusfabrics.com", "WORKER", "GARMENT", "IRON_OP"),
          new SeedUserProfile(
              "Peter", "Packer", "pack.op@nexusfabrics.com", "WORKER", "GARMENT", "PACK_OP"),

          // ── 5. Quality Control & Laboratory — QUALITY ──
          new SeedUserProfile(
              "Quinn", "Ashford", "qa.mgr@nexusfabrics.com", "MANAGER", "QUALITY", "QA_MGR"),
          new SeedUserProfile(
              "Cora", "Colby", "colorist@nexusfabrics.com", "WORKER", "QUALITY", "COLORIST"),
          new SeedUserProfile(
              "Laura", "Labson", "lab.tech@nexusfabrics.com", "WORKER", "QUALITY", "LAB_TECH"),
          new SeedUserProfile(
              "Yara", "Yarnley", "yarn.qc@nexusfabrics.com", "WORKER", "QUALITY", "YARN_QC"),
          new SeedUserProfile(
              "Fiona", "Fabris", "fabric.qc@nexusfabrics.com", "WORKER", "QUALITY", "FABRIC_QC"),
          new SeedUserProfile(
              "Grace", "Garland", "garm.qc@nexusfabrics.com", "WORKER", "QUALITY", "GARM_QC"),

          // ── 6. Logistics & Supply Chain — WAREHOUSE / SHIPPING ──
          new SeedUserProfile(
              "Sam", "Chain", "sc.mgr@nexusfabrics.com", "MANAGER", "WAREHOUSE", "SC_MGR"),
          new SeedUserProfile(
              "William", "House", "wh.mgr@nexusfabrics.com", "MANAGER", "WAREHOUSE", "WH_MGR"),
          new SeedUserProfile(
              "Yusuf", "Stock", "yarn.wh@nexusfabrics.com", "WORKER", "WAREHOUSE", "YARN_WH"),
          new SeedUserProfile(
              "Fatima", "Bolt", "fab.wh@nexusfabrics.com", "WORKER", "WAREHOUSE", "FAB_WH"),
          new SeedUserProfile(
              "Chris", "Hazard", "chem.wh@nexusfabrics.com", "WORKER", "WAREHOUSE", "CHEM_WH"),
          new SeedUserProfile(
              "Logan", "Freight", "log.coord@nexusfabrics.com", "WORKER", "SHIPPING", "LOG_COORD"),
          new SeedUserProfile(
              "Derek",
              "Dispatch",
              "dispatcher@nexusfabrics.com",
              "WORKER",
              "SHIPPING",
              "DISPATCHER"),

          // ── 7. Procurement & Sourcing — PROCUREMENT ──
          new SeedUserProfile(
              "Philip",
              "Proctor",
              "proc.mgr@nexusfabrics.com",
              "MANAGER",
              "PROCUREMENT",
              "PROC_MGR"),
          new SeedUserProfile(
              "Yolanda",
              "Bidwell",
              "yarn.buyer@nexusfabrics.com",
              "WORKER",
              "PROCUREMENT",
              "YARN_BUYER"),
          new SeedUserProfile(
              "Carmen",
              "Briggs",
              "chem.buyer@nexusfabrics.com",
              "WORKER",
              "PROCUREMENT",
              "CHEM_BUYER"),
          new SeedUserProfile(
              "Sofia", "Source", "sourcer@nexusfabrics.com", "WORKER", "PROCUREMENT", "SOURCER"),

          // ── 8. Sales & Merchandising — SALES ──
          new SeedUserProfile(
              "Steven", "Seller", "sales.dir@nexusfabrics.com", "MANAGER", "SALES", "SALES_DIR"),
          new SeedUserProfile(
              "Sandra", "Deal", "sales.rep@nexusfabrics.com", "WORKER", "SALES", "SALES_REP"),
          new SeedUserProfile(
              "Marco", "Mercer", "sr.merch@nexusfabrics.com", "SUPERVISOR", "SALES", "SR_MERCH"),
          new SeedUserProfile(
              "Maya", "Merchant", "merch@nexusfabrics.com", "WORKER", "SALES", "MERCH"),
          new SeedUserProfile(
              "Stella", "Shaw", "showroom@nexusfabrics.com", "WORKER", "SALES", "SHOWROOM"),

          // ── 9. Design, R&D & Planning — PLANNING / RD ──
          new SeedUserProfile(
              "Patrick", "Planner", "plan.mgr@nexusfabrics.com", "MANAGER", "PLANNING", "PLAN_MGR"),
          new SeedUserProfile(
              "Priya", "Planwell", "planner@nexusfabrics.com", "WORKER", "PLANNING", "PLANNER"),
          new SeedUserProfile(
              "Robert", "Reed", "rd.mgr@nexusfabrics.com", "MANAGER", "RD", "RD_MGR"),
          new SeedUserProfile(
              "Flora", "Design", "fab.design@nexusfabrics.com", "WORKER", "RD", "FAB_DESIGN"),
          new SeedUserProfile(
              "Thomas", "Techwell", "technolog@nexusfabrics.com", "WORKER", "RD", "TECHNOLOG"),
          new SeedUserProfile(
              "Selma", "Sample", "sample.mkr@nexusfabrics.com", "WORKER", "RD", "SAMPLE_MKR"),

          // ── 10. HR ──
          new SeedUserProfile(
              "Helen", "Hart", "hr.mgr@nexusfabrics.com", "MANAGER", "HR", "HR_MGR"),
          new SeedUserProfile(
              "Nina", "Newland", "recruiter@nexusfabrics.com", "WORKER", "HR", "RECRUITER"),
          new SeedUserProfile(
              "Harry", "Handbook", "hr.spec@nexusfabrics.com", "SUPERVISOR", "HR", "HR_SPEC"),

          // ── 11. Finance ──
          new SeedUserProfile(
              "Fred", "Fiscal", "cfo@nexusfabrics.com", "MANAGER", "FINANCE", "CFO"),
          new SeedUserProfile(
              "Anna", "Audit", "accountant@nexusfabrics.com", "WORKER", "FINANCE", "ACCOUNTANT"),
          new SeedUserProfile(
              "Betty",
              "Budget",
              "budget@nexusfabrics.com",
              "SUPERVISOR",
              "FINANCE",
              "BUDGET_ANALYST"));

  /**
   * Sentinel email: the LAST profile in the list. If this user exists, we know the full seed
   * completed successfully. Per-user idempotency in seedUser() handles partial runs.
   */
  private static final String LAST_SEEDED_EMAIL = "budget@nexusfabrics.com";

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
          // Check the LAST user in the list — if it exists, the full seed completed.
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
                OrganizationDto rootOrg =
                    organizationService
                        .getRootOrganization()
                        .orElseThrow(() -> new IllegalStateException("Root organization missing"));

                int seededCount = 0;
                for (SeedUserProfile profile : ALL_PROFILES) {
                  if (seedUser(profile, tenant.getId(), rootOrg.getId())) {
                    seededCount++;
                  }
                }
                log.info(
                    "Seeded {}/{} playground users for tenant: {}",
                    seededCount,
                    ALL_PROFILES.size(),
                    tenant.getId());
              });
        });
  }

  /** Returns true if a new user was actually created, false if skipped (already existed). */
  private boolean seedUser(SeedUserProfile profile, UUID tenantId, UUID orgId) {
    if (userRepository.existsByTenantIdAndContactValue(tenantId, profile.email())) {
      return false;
    }

    // 1. Resolve Role
    Role role =
        roleService
            .findByCode(profile.roleCode())
            .orElseThrow(() -> new IllegalStateException("Role not found: " + profile.roleCode()));

    // 2. Resolve Department
    UUID departmentId = null;
    if (profile.departmentCode() != null) {
      Optional<Department> deptOpt =
          departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentCode(
              tenantId, orgId, profile.departmentCode());
      if (deptOpt.isPresent()) {
        departmentId = deptOpt.get().getId();
      } else {
        log.warn(
            "Seed User [{}]: Department code '{}' not found, skipping assignment.",
            profile.email(),
            profile.departmentCode());
      }
    }

    CreateInternalUserRequest req =
        CreateInternalUserRequest.builder()
            .firstName(profile.firstName())
            .lastName(profile.lastName())
            .contactValue(profile.email())
            .contactType(ContactType.EMAIL)
            .organizationId(orgId)
            .departmentId(departmentId)
            .roleId(role.getId())
            .jobTitleCode(profile.jobTitleCode())
            .build();

    UserDto user = userCreationService.createInternalUser(req);

    // 3. Setup Password System ByPass
    setupAuthUser(user.getId(), tenantId);

    // 4. Mark all auto-assigned contacts as verified to allow login
    contactRepository
        .findByTenantIdAndContactValue(tenantId, profile.email())
        .ifPresent(
            contact -> {
              contact.verify();
              contactRepository.save(contact);
            });

    log.debug(
        "Created user: {} {} — {} ({})",
        profile.firstName(),
        profile.lastName(),
        profile.email(),
        profile.roleCode());
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
    return 30;
  }
}
