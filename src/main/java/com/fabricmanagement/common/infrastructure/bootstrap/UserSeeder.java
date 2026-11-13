package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeder for initial employees and users.
 *
 * <p>Generates the persona users (one tenant administrator plus job-title-specific personas) for
 * the Nexus Fabrics sample company, and the personas of registered demo companies. Each user has a
 * thematic name that hints at their role for intuitive simulation UX.
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
  private final IdentityProvisioningService identityProvisioningService;

  private static final String DEFAULT_PASSWORD = "password123";

  /** Which personas a registered demo company receives. */
  public enum PersonaSubset {
    /**
     * The personas of one order's journey through this company (PLATFORM-BOUNDARY-1): a core set
     * every company gets, plus a manager and an operator for each production area its organisation
     * type works in. Chosen from the company's root organisation.
     */
    REPRESENTATIVE
  }

  /**
   * Seed user profile definition. Each entry maps to exactly one user in the playground tenant.
   *
   * @param firstName Thematic first name (hints at role)
   * @param lastName Thematic last name (hints at domain)
   * @param email Unique, code-derived email address
   * @param roleCode ADMIN, MANAGER, SUPERVISOR or WORKER (never a platform role)
   * @param departmentCode OrganizationSeeder department code (nullable for admins)
   * @param jobTitleCode JobTitleSeedData preset code (nullable for admins)
   */
  private record SeedUserProfile(
      String firstName,
      String lastName,
      String email,
      String roleCode,
      String departmentCode,
      String jobTitleCode) {
    SeedUserProfile withEmail(String newEmail) {
      return new SeedUserProfile(
          firstName, lastName, newEmail, roleCode, departmentCode, jobTitleCode);
    }

    String aliasSlug() {
      String localPart = email.substring(0, email.indexOf('@'));
      return localPart
          .toLowerCase(Locale.ROOT)
          .replaceAll("[^a-z0-9]+", "-")
          .replaceAll("^-|-$", "");
    }
  }

  // ─── Thematic Persona Definitions ───

  /**
   * All seed user profiles. Names are chosen to be memorable and to subtly hint at the user's role
   * in the textile value chain. For example, "Spencer Mills" is the Spinning Mill Manager (Spencer
   * ≈ Spin, Mills = factory), "Dylan Hue" manages the Dyehouse (Dye ≈ Dylan, Hue = color).
   */
  private static final List<SeedUserProfile> ALL_PROFILES =
      List.of(
          // ── Tenant administrator ── (platform staff never live in a demo tenant: they belong to
          // Longward's platform tenant, see PlatformOperatorDevSeeder)
          new SeedUserProfile("Adrian", "Nexus", "admin@nexusfabrics.com", "ADMIN", null, null),

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

  /**
   * Every company's personas, in the order an order passes them: sales, planning, purchasing,
   * warehouse, finance and quality. Sandra Deal and Yolanda Bidwell are named on purpose: the sales
   * and purchasing demo data are recorded as their work.
   */
  static final List<String> CORE_PERSONA_EMAILS =
      List.of(
          "sales.rep@nexusfabrics.com",
          "planner@nexusfabrics.com",
          "yarn.buyer@nexusfabrics.com",
          "wh.mgr@nexusfabrics.com",
          "accountant@nexusfabrics.com",
          "qa.mgr@nexusfabrics.com");

  /** A manager and an operator for each production area. */
  static final List<String> SPINNING_PERSONA_EMAILS =
      List.of("spin.mgr@nexusfabrics.com", "spin.op@nexusfabrics.com");

  static final List<String> WEAVING_PERSONA_EMAILS =
      List.of("weav.mgr@nexusfabrics.com", "weaver@nexusfabrics.com");

  static final List<String> KNITTING_PERSONA_EMAILS =
      List.of("knit.mgr@nexusfabrics.com", "knitter@nexusfabrics.com");

  static final List<String> DYEING_PERSONA_EMAILS =
      List.of("dye.mgr@nexusfabrics.com", "jet.op@nexusfabrics.com");

  /**
   * The personas a company of this organisation type sees: the core set plus its production areas.
   * An integrated mill works in every area. A garment maker gets the core set only, since garment
   * production is not yet part of FabricOS. An unknown type gets every area rather than too few.
   */
  static List<String> personaEmailsFor(OrganizationType organizationType) {
    List<List<String>> areas =
        switch (organizationType == null ? OrganizationType.VERTICAL_MILL : organizationType) {
          case SPINNER -> List.of(SPINNING_PERSONA_EMAILS);
          case WEAVER -> List.of(WEAVING_PERSONA_EMAILS);
          case KNITTER -> List.of(KNITTING_PERSONA_EMAILS);
          case DYER_FINISHER -> List.of(DYEING_PERSONA_EMAILS);
          case VERTICAL_MILL ->
              List.of(
                  SPINNING_PERSONA_EMAILS,
                  WEAVING_PERSONA_EMAILS,
                  KNITTING_PERSONA_EMAILS,
                  DYEING_PERSONA_EMAILS);
          case GARMENT_MANUFACTURER, EXTERNAL_PARTNER -> List.of();
        };
    return Stream.concat(CORE_PERSONA_EMAILS.stream(), areas.stream().flatMap(List::stream))
        .toList();
  }

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

  public int seedFor(UUID tenantId, String ownerEmail, PersonaSubset subset) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          Integer seeded =
              transactionTemplate.execute(
                  status -> {
                    OrganizationDto rootOrg =
                        organizationService
                            .getRootOrganization()
                            .orElseThrow(
                                () -> new IllegalStateException("Root organization missing"));

                    List<SeedUserProfile> profiles =
                        profilesFor(subset, rootOrg.getOrganizationType());
                    int seededCount = 0;
                    for (SeedUserProfile profile : profiles) {
                      SeedUserProfile tenantProfile =
                          profile.withEmail(ownerAliasEmail(ownerEmail, profile));
                      if (seedUser(tenantProfile, tenantId, rootOrg.getId())) {
                        seededCount++;
                      }
                    }
                    log.info(
                        "Seeded {}/{} registered demo persona users for tenant: {}",
                        seededCount,
                        profiles.size(),
                        tenantId);
                    return seededCount;
                  });
          return seeded != null ? seeded : 0;
        });
  }

  private List<SeedUserProfile> profilesFor(
      PersonaSubset subset, OrganizationType organizationType) {
    if (subset != PersonaSubset.REPRESENTATIVE) {
      throw new IllegalArgumentException("Unsupported persona subset: " + subset);
    }
    Map<String, SeedUserProfile> byEmail =
        ALL_PROFILES.stream()
            .collect(Collectors.toMap(SeedUserProfile::email, Function.identity()));
    return personaEmailsFor(organizationType).stream()
        .map(
            email ->
                Optional.ofNullable(byEmail.get(email))
                    .orElseThrow(() -> new IllegalStateException("Unknown persona: " + email)))
        .toList();
  }

  private String ownerAliasEmail(String ownerEmail, SeedUserProfile profile) {
    String normalizedOwnerEmail = ownerEmail.trim().toLowerCase(Locale.ROOT);
    int atIndex = normalizedOwnerEmail.indexOf('@');
    if (atIndex <= 0 || atIndex == normalizedOwnerEmail.length() - 1) {
      throw new IllegalArgumentException("Owner email is required for demo persona aliases");
    }
    String localPart = normalizedOwnerEmail.substring(0, atIndex);
    String domain = normalizedOwnerEmail.substring(atIndex + 1);
    return localPart + "+" + profile.aliasSlug() + "@" + domain;
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
            .invitationEmailSuppressed(true)
            .build();

    UserDto user = userCreationService.createInternalUser(req);
    stampDemoSeed(user.getId(), tenantId);

    // 3. Mark all auto-assigned contacts as verified to allow login
    contactRepository
        .findByTenantIdAndContactValue(tenantId, profile.email())
        .ifPresent(
            contact -> {
              contact.verify();
              contactRepository.save(contact);
            });

    // 4. Setup Password System ByPass + platform identity dual-write
    setupAuthUser(user.getId(), tenantId, profile.email());

    log.debug(
        "Created user: {} {} — {} ({})",
        profile.firstName(),
        profile.lastName(),
        profile.email(),
        profile.roleCode());
    return true;
  }

  private void stampDemoSeed(UUID userId, UUID tenantId) {
    User seededUser =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(
                () -> new IllegalStateException("Seeded user not found after creation: " + userId));
    seededUser.setDemoSeed(true);
    userRepository.save(seededUser);
  }

  private void setupAuthUser(UUID userId, UUID tenantId, String email) {
    if (!authUserRepository.existsByUserId(userId)) {
      String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);
      AuthUser authUser = AuthUser.create(userId, passwordHash);
      authUser.setTenantId(tenantId);
      authUser.verify();
      authUserRepository.save(authUser);
      identityProvisioningService.provisionCredential(
          email, passwordHash, false, MfaType.NONE, null, true, tenantId, userId);
    }
  }

  @Override
  public int getOrder() {
    return 30;
  }
}
