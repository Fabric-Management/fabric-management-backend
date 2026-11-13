package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundPersonaDto;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaygroundService {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final TenantAccessPort tenantAccessPort;

  private static final Map<String, String> PLAYGROUND_JOB_TITLES =
      Map.ofEntries(
          Map.entry("admin@nexusfabrics.com", "System Admin"),
          Map.entry("spin.mgr@nexusfabrics.com", "Spinning Mill Manager"),
          Map.entry("spin.super@nexusfabrics.com", "Spinning Supervisor"),
          Map.entry("blow.op@nexusfabrics.com", "Blow Room Operator"),
          Map.entry("card.op@nexusfabrics.com", "Carding Operator"),
          Map.entry("spin.op@nexusfabrics.com", "Spinning Operator"),
          Map.entry("wind.op@nexusfabrics.com", "Winding Operator"),
          Map.entry("weav.mgr@nexusfabrics.com", "Weaving Manager"),
          Map.entry("weav.super@nexusfabrics.com", "Weaving Supervisor"),
          Map.entry("knit.mgr@nexusfabrics.com", "Knitting Manager"),
          Map.entry("knit.super@nexusfabrics.com", "Knitting Supervisor"),
          Map.entry("warp.op@nexusfabrics.com", "Warping Operator"),
          Map.entry("weaver@nexusfabrics.com", "Loom Operator"),
          Map.entry("knitter@nexusfabrics.com", "Knitting Operator"),
          Map.entry("dye.mgr@nexusfabrics.com", "Dyehouse Manager"),
          Map.entry("finish.mgr@nexusfabrics.com", "Finishing Manager"),
          Map.entry("dye.super@nexusfabrics.com", "Dyeing Supervisor"),
          Map.entry("chemist@nexusfabrics.com", "Textile Chemist"),
          Map.entry("dye.mixer@nexusfabrics.com", "Dye Mixer"),
          Map.entry("jet.op@nexusfabrics.com", "Jet Dyeing Operator"),
          Map.entry("stenter.op@nexusfabrics.com", "Stenter Operator"),
          Map.entry("print.op@nexusfabrics.com", "Printing Operator"),
          Map.entry("garm.mgr@nexusfabrics.com", "Garment Factory Manager"),
          Map.entry("line.mgr@nexusfabrics.com", "Production Line Manager"),
          Map.entry("pattern@nexusfabrics.com", "Pattern Maker"),
          Map.entry("cut.super@nexusfabrics.com", "Cutting Supervisor"),
          Map.entry("sew.op@nexusfabrics.com", "Sewing Machine Operator"),
          Map.entry("iron.op@nexusfabrics.com", "Finishing Presser"),
          Map.entry("pack.op@nexusfabrics.com", "Packing Operator"),
          Map.entry("qa.mgr@nexusfabrics.com", "QA Manager"),
          Map.entry("colorist@nexusfabrics.com", "Colorist"),
          Map.entry("lab.tech@nexusfabrics.com", "Lab Technician"),
          Map.entry("yarn.qc@nexusfabrics.com", "Yarn QC Inspector"),
          Map.entry("fabric.qc@nexusfabrics.com", "Fabric QC Inspector"),
          Map.entry("garm.qc@nexusfabrics.com", "Garment QC Inspector"),
          Map.entry("sc.mgr@nexusfabrics.com", "Supply Chain Manager"),
          Map.entry("wh.mgr@nexusfabrics.com", "Warehouse Manager"),
          Map.entry("yarn.wh@nexusfabrics.com", "Yarn Warehouse Clerk"),
          Map.entry("fab.wh@nexusfabrics.com", "Fabric Warehouse Clerk"),
          Map.entry("chem.wh@nexusfabrics.com", "Chemical Warehouse Clerk"),
          Map.entry("log.coord@nexusfabrics.com", "Logistics Coordinator"),
          Map.entry("dispatcher@nexusfabrics.com", "Dispatcher"),
          Map.entry("proc.mgr@nexusfabrics.com", "Procurement Manager"),
          Map.entry("yarn.buyer@nexusfabrics.com", "Yarn & Fiber Buyer"),
          Map.entry("chem.buyer@nexusfabrics.com", "Chemical Buyer"),
          Map.entry("sourcer@nexusfabrics.com", "Sourcing Specialist"),
          Map.entry("sales.dir@nexusfabrics.com", "Sales Director"),
          Map.entry("sales.rep@nexusfabrics.com", "Sales Representative"),
          Map.entry("sr.merch@nexusfabrics.com", "Senior Merchandiser"),
          Map.entry("merch@nexusfabrics.com", "Merchandiser"),
          Map.entry("showroom@nexusfabrics.com", "Showroom Coordinator"),
          Map.entry("plan.mgr@nexusfabrics.com", "Planning Manager"),
          Map.entry("planner@nexusfabrics.com", "Production Planner"),
          Map.entry("rd.mgr@nexusfabrics.com", "R&D Manager"),
          Map.entry("fab.design@nexusfabrics.com", "Fabric Designer"),
          Map.entry("technolog@nexusfabrics.com", "Fabric Technologist"),
          Map.entry("sample.mkr@nexusfabrics.com", "Sample Maker"),
          Map.entry("hr.mgr@nexusfabrics.com", "HR Manager"),
          Map.entry("recruiter@nexusfabrics.com", "Recruiter"),
          Map.entry("hr.spec@nexusfabrics.com", "HR Specialist"),
          Map.entry("cfo@nexusfabrics.com", "Chief Financial Officer"),
          Map.entry("accountant@nexusfabrics.com", "Accountant"),
          Map.entry("budget@nexusfabrics.com", "Budget Analyst"),
          Map.entry("oscar@ozcotton.com", "Supplier Owner"),
          Map.entry("accounting@ozcotton.com", "Supplier Accountant"),
          Map.entry("buyer@globalfashion.com", "Customer Buyer"),
          Map.entry("merchandiser@globalfashion.com", "Customer Merchandiser"),
          Map.entry("manager@southdyeing.com", "Subcontractor Owner"),
          Map.entry("logistics@southdyeing.com", "Subcontractor Dispatcher"),
          Map.entry("ops@fastlogistics.com", "Logistics Partner"),
          Map.entry("trade@centraltextile.com", "Trading Partner Owner"),
          Map.entry("finance@centraltextile.com", "Partner Accountant"));

  /**
   * A token for viewing the demo as {@code userId}, an active user of the same demo tenant.
   *
   * <p>The caller is the tenant's signed-in owner. The token is a normal-lifetime demo-persona
   * token that the caller's browser tab holds for itself; the owner's own session (cookies) is not
   * touched. The anonymous Playground session, which used a cookie instead, is retired
   * (PLATFORM-BOUNDARY-1).
   */
  @Transactional
  public PlaygroundImpersonateResponse impersonate(UUID tenantId, UUID userId, String guestId) {
    requireDemoMode(tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          // An inactive user is no persona: viewing as them must fail, not succeed silently.
          User targetUser =
              userRepository
                  .findByTenantIdAndId(tenantId, userId)
                  .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                  .orElseThrow(
                      () ->
                          new PlatformDomainException(
                              "This user cannot be viewed as a demo persona",
                              "IMPERSONATION_TARGET_UNAVAILABLE",
                              404));

          String contactValue = resolvePlaygroundContact(targetUser);
          String newToken =
              jwtService.generateDemoImpersonationAccessToken(targetUser, guestId, contactValue);

          String roleName =
              targetUser.getRole() != null ? targetUser.getRole().getRoleName() : "No Role";

          return new PlaygroundImpersonateResponse(
              newToken, targetUser.getId(), targetUser.getDisplayName(), roleName);
        });
  }

  @Transactional(readOnly = true)
  public List<PlaygroundPersonaDto> listPersonas(UUID tenantId) {
    requireDemoMode(tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          List<User> users = userRepository.findByTenantIdWithRelations(tenantId);
          userRepository.findByTenantIdWithContacts(tenantId);

          Map<UUID, Organization> orgMap =
              organizationRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                  .collect(Collectors.toMap(Organization::getId, Function.identity()));

          return users.stream()
              .map(
                  u -> {
                    var primaryDept =
                        u.getUserDepartments().stream()
                            .filter(ud -> Boolean.TRUE.equals(ud.getIsPrimary()))
                            .findFirst();

                    Organization org = orgMap.get(u.getOrganizationId());
                    String email = extractPrimaryEmail(u);
                    String roleName = u.getRole() != null ? u.getRole().getRoleName() : "no role";
                    String jobTitle = jobTitleFor(email).orElse(roleName);

                    return new PlaygroundPersonaDto(
                        u.getId(),
                        u.getDisplayName(),
                        email,
                        roleName,
                        jobTitle,
                        primaryDept
                            .map(ud -> ud.getDepartment().getDepartmentName())
                            .orElse("No Department"),
                        primaryDept.map(ud -> ud.getDepartment().getDepartmentGroup()).orElse(null),
                        u.getUserType().name(),
                        org != null ? org.getName() : "Unknown");
                  })
              .toList();
        });
  }

  /**
   * A persona's job title. Registered demo companies give their personas aliases of the owner's own
   * address ({@code owner+weav-mgr@company.example}), so the title is found by the persona's slug
   * as well as by the sample company's address.
   */
  static Optional<String> jobTitleFor(String email) {
    if (email == null) {
      return Optional.empty();
    }
    String normalised = email.trim().toLowerCase(Locale.ROOT);
    String direct = PLAYGROUND_JOB_TITLES.get(normalised);
    if (direct != null) {
      return Optional.of(direct);
    }
    int at = normalised.indexOf('@');
    // The persona slug is the last tag: the owner's own address may already carry one.
    int plus = normalised.lastIndexOf('+');
    if (plus < 0 || at < plus) {
      return Optional.empty();
    }
    return Optional.ofNullable(JOB_TITLES_BY_SLUG.get(normalised.substring(plus + 1, at)));
  }

  private static final Map<String, String> JOB_TITLES_BY_SLUG =
      PLAYGROUND_JOB_TITLES.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> slug(entry.getKey()),
                  Map.Entry::getValue,
                  (first, second) -> {
                    throw new IllegalStateException("Two personas share a slug: " + first);
                  }));

  private static String slug(String email) {
    return email
        .substring(0, email.indexOf('@'))
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private void requireDemoMode(UUID tenantId) {
    boolean demoMode;
    try {
      demoMode = tenantAccessPort.isDemoMode(tenantId);
    } catch (RuntimeException ex) {
      log.warn(
          "Could not resolve demo mode for tenant {}; refusing playground capability", tenantId);
      demoMode = false;
    }
    if (!demoMode) {
      throw new PlatformDomainException(
          "Demo mode is required to use playground impersonation", "DEMO_MODE_REQUIRED", 403);
    }
  }

  private String resolvePlaygroundContact(User user) {
    return user.getAnyVerifiedContact()
        .or(() -> user.getDefaultContact())
        .map(c -> c.getContactValue())
        .orElse(
            user.getFirstName().toLowerCase()
                + "."
                + user.getLastName().toLowerCase()
                + "@playground.local");
  }

  private String extractPrimaryEmail(User user) {
    return user.getUserContacts().stream()
        .map(uc -> uc.getContact())
        .filter(c -> c != null && "EMAIL".equals(c.getContactType().name()))
        .map(c -> c.getContactValue())
        .findFirst()
        .orElse("—");
  }
}
