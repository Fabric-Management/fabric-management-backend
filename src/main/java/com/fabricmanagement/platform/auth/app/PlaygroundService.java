package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundInitResponse;
import com.fabricmanagement.platform.auth.dto.PlaygroundPersonaDto;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaygroundService {

  private final TenantClonerService tenantClonerService;
  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final TransactionTemplate transactionTemplate;
  private final TenantAccessPort tenantAccessPort;

  private static final Map<String, String> PLAYGROUND_JOB_TITLES =
      Map.ofEntries(
          Map.entry("admin@nexusfabrics.com", "System Admin"),
          Map.entry("admin@fabricos.io", "Platform Admin"),
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
   * Initializes a playground session by cloning the template tenant and selecting a default
   * persona.
   *
   * <p><b>Why no @Transactional here:</b> The clone step uses BYPASSRLS (SystemTransactionExecutor)
   * with its own JDBC connection. If we wrapped this method in @Transactional, Spring would open a
   * Hibernate Session (and DB connection) BEFORE TenantContext is set — binding it to
   * SYSTEM_TENANT_ID. The subsequent JPA read would reuse that connection, and RLS would filter out
   * all rows for the new playground tenant, returning an empty user list.
   *
   * <p>Instead, we let the clone run in its own transaction, then set TenantContext and open a NEW
   * read-only transaction via TransactionTemplate so the connection is bound to the correct tenant.
   *
   * @deprecated Register-first playground tenants with {@code demoMode} are the supported entry;
   *     anonymous init is retired pending FE migration.
   */
  @Deprecated
  public PlaygroundInitResponse initPlayground(String guestId) {
    // 1. Clone the template (runs in its own BYPASSRLS transaction)
    Tenant playgroundTenant = tenantClonerService.cloneTemplateToPlayground();

    // 2. Find the CEO / Platform Admin to be the default persona.
    //    Must open a NEW transaction AFTER setting TenantContext so Hibernate binds
    //    the connection to the correct playground tenant for RLS.
    return TenantContext.executeInTenantContext(
        playgroundTenant.getId(),
        () ->
            transactionTemplate.execute(
                status -> {
                  // Two-pass fetch to avoid MultipleBagFetchException.
                  List<User> activeUsers =
                      userRepository.findByTenantIdWithRelations(playgroundTenant.getId());
                  userRepository.findByTenantIdWithContacts(playgroundTenant.getId());

                  // Try preferred roles first, then fall back to any user
                  User defaultUser =
                      activeUsers.stream()
                          .filter(
                              u ->
                                  u.getRole() != null
                                      && ("PLATFORM_ADMIN".equals(u.getRole().getRoleCode())
                                          || "MANAGER".equals(u.getRole().getRoleCode())))
                          .findFirst()
                          .or(
                              () ->
                                  activeUsers.stream().filter(u -> u.getRole() != null).findFirst())
                          .or(() -> activeUsers.stream().findFirst())
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "No active users found in cloned playground tenant"));

                  String contactValue = resolvePlaygroundContact(defaultUser);
                  String token =
                      jwtService.generatePlaygroundAccessToken(defaultUser, guestId, contactValue);

                  String roleName =
                      defaultUser.getRole() != null
                          ? defaultUser.getRole().getRoleName()
                          : "No Role";

                  Organization org =
                      organizationRepository
                          .findByTenantIdAndIsActiveTrue(playgroundTenant.getId())
                          .stream()
                          .filter(o -> o.getOrganizationType() != OrganizationType.EXTERNAL_PARTNER)
                          .findFirst()
                          .orElse(null);

                  return new PlaygroundInitResponse(
                      guestId,
                      token,
                      playgroundTenant.getId(),
                      defaultUser.getId(),
                      defaultUser.getDisplayName(),
                      roleName,
                      (org != null && org.getOrganizationType() != null)
                          ? org.getOrganizationType().name()
                          : "VERTICAL_MILL");
                }));
  }

  @Transactional
  public PlaygroundImpersonateResponse impersonate(UUID tenantId, UUID userId, String guestId) {
    return impersonate(tenantId, userId, guestId, false);
  }

  @Transactional
  public PlaygroundImpersonateResponse impersonate(
      UUID tenantId, UUID userId, String guestId, boolean preserveRealSession) {
    requireDemoMode(tenantId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          User targetUser =
              userRepository
                  .findByTenantIdAndId(tenantId, userId)
                  .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

          String contactValue = resolvePlaygroundContact(targetUser);
          String newToken =
              preserveRealSession
                  ? jwtService.generateDemoImpersonationAccessToken(
                      targetUser, guestId, contactValue)
                  : jwtService.generatePlaygroundAccessToken(targetUser, guestId, contactValue);

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
                    String jobTitle = PLAYGROUND_JOB_TITLES.getOrDefault(email, roleName);

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
