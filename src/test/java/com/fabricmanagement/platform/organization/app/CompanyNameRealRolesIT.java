package com.fabricmanagement.platform.organization.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.tenant.TenantNamePort;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.app.adapter.TenantNameAdapter;
import com.fabricmanagement.platform.user.app.OnboardingJourneyService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import com.fabricmanagement.platform.user.dto.OnboardingStepPayload;
import com.fabricmanagement.platform.user.dto.SaveOnboardingStepRequest;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(addFilters = false)
class CompanyNameRealRolesIT extends AbstractFabricAppIntegrationTest {

  private static final String ORIGINAL = "Hawthorn Weaving Ltd";
  private static final String RENAMED = "Willow Cloth Ltd";
  private static final String ORIGINAL_TAX_ID = "GB123456789";

  @Autowired private OrganizationRepository organizations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private PermissionOverrideRepository overrides;
  @Autowired private PermissionEvaluator permissions;
  @Autowired private OnboardingJourneyService journeys;
  @Autowired private OnboardingJourneyProvisioningPort provisioning;
  @Autowired private SignupEvidencePort evidence;
  @Autowired private TenantQueryPort tenants;
  @Autowired private TenantNamePort tenantNames;
  @Autowired private TenantSessionBinder sessions;
  @Autowired private TransactionTemplate transactions;
  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  @MockitoSpyBean private TenantNameAdapter tenantWriter;

  private UUID tenant;
  private UUID root;
  private UUID admin;
  private UUID member;

  @BeforeEach
  void setUp() {
    tenant = createTenant();
    TenantContext.setCurrentTenantId(tenant);
    var organization = Organization.create(ORIGINAL, ORIGINAL_TAX_ID, OrganizationType.WEAVER);
    organization.setPreferredCurrency("GBP");
    root = organizations.saveAndFlush(organization).getId();
    admin = createMember("ADMIN", false);
    authenticate(admin, "ADMIN");
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void onboardingWritesBothNamesAndMembershipReadsTheCommittedName() throws Exception {
    UUID signup = UUID.randomUUID();
    var facts =
        new CompanyEvidence(
            new CompanyEvidence.Organisation(
                ORIGINAL, ORIGINAL_TAX_ID, OrganizationType.WEAVER, null, null, null, null, null),
            null,
            new CompanyEvidence.Registrant("Eleanor", "Hughes", "eleanor@example.com", null),
            null,
            List.of("FabricOS"),
            Set.of(
                "organizationName",
                "taxId",
                "organizationType",
                "firstName",
                "lastName",
                "workEmail",
                "selectedOS"));
    transactions.executeWithoutResult(
        status -> {
          evidence.capture(
              new SignupEvidencePort.SignupCompany(signup, tenant, admin, "TRIAL", facts, null));
          provisioning.provision(tenant, admin, signup, "TRIAL");
        });
    journeys.save(
        OnboardingStepId.ORGANIZATION_DETAILS,
        new SaveOnboardingStepRequest(
            0L,
            UUID.randomUUID(),
            new OnboardingStepPayload.OrganizationDetails(
                RENAMED,
                ORIGINAL_TAX_ID,
                OrganizationType.WEAVER,
                null,
                null,
                "TEXTILE",
                null,
                null)));

    assertNames(RENAMED);
    assertMembershipName(RENAMED);
  }

  @Test
  void adminSettingsUpdateWritesBothNamesAndRepairsAnAlreadySavedRootName() throws Exception {
    update(root, RENAMED, "GBP").andExpect(status().isOk());
    assertNames(RENAMED);
    assertMembershipName(RENAMED);

    systemJdbc.update(
        "UPDATE common_tenant.common_tenant SET name = ? WHERE id = ?", ORIGINAL, tenant);
    update(root, RENAMED, "GBP").andExpect(status().isOk());
    assertNames(RENAMED);
  }

  @Test
  void tenantWriteFailureRollsBackTheCompanyUpdate() throws Exception {
    doThrow(
            new PlatformDomainException(
                "Injected write failure", "ORG_TENANT_NAME_UPDATE_FAILED", 409))
        // The bean is a transactional (MANDATORY) proxy: stubbing through it would call rename()
        // outside a transaction. Stub the spy behind the proxy.
        .when(AopTestUtils.<TenantNameAdapter>getUltimateTargetObject(tenantWriter))
        .rename(eq(tenant), any(String.class));

    update(root, RENAMED, "GBP").andExpect(status().isConflict());
    assertOriginalProfile();
  }

  @Test
  void failureAfterTenantWriteRollsBackBothNames() throws Exception {
    update(root, RENAMED, "INVALID")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ORG_INVALID_PREFERRED_CURRENCY"));
    assertOriginalProfile();
  }

  @Test
  void tenantPortCannotWriteAnotherTenantsName() {
    UUID otherTenant = createTenant();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      sessions.bindToCurrentSession(tenant);
                      tenantNames.rename(otherTenant, RENAMED);
                    }))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure ->
                assertThat(failure.getErrorCode()).isEqualTo("ORG_TENANT_NAME_UPDATE_FAILED"));
    assertNames(ORIGINAL);
    assertThat(tenants.findById(otherTenant).orElseThrow().name()).isEqualTo(ORIGINAL);
  }

  @Test
  void managerAndDepartmentGrantCannotChangeAnyRootProfileFields() throws Exception {
    UUID manager = createMember("MANAGER", false);
    authenticate(manager, "MANAGER");
    grant(manager, "members", "manage", DataScope.ORGANIZATION);
    assertThat(
            permissions
                .evaluateFresh(tenant, "MANAGER", List.of(), manager)
                .can("members", "manage"))
        .isTrue();
    assertRootUpdateDenied("ONBOARDING_COMPANY_PERMISSION_REQUIRED");

    grant(manager, "organization", "configure", DataScope.DEPARTMENT);
    assertThat(
            permissions
                .evaluateFresh(tenant, "MANAGER", List.of(), manager)
                .scopeOf("organization", "configure"))
        .isEqualTo(DataScope.DEPARTMENT);
    assertRootUpdateDenied("ONBOARDING_COMPANY_PERMISSION_REQUIRED");
  }

  @Test
  void demoPersonaCannotChangeTheRootEvenWithAdminAuthority() throws Exception {
    authenticate(createMember("ADMIN", true), "ADMIN");
    assertRootUpdateDenied("ONBOARDING_COMPANY_REQUIRES_REAL_MEMBER");
  }

  @Test
  void childAndPartnerUpdatesRespectTheOrganisationBoundary() throws Exception {
    var child = Organization.create("Hawthorn Warehouse", null, OrganizationType.WEAVER);
    child.setParent(root);
    UUID childId = organizations.saveAndFlush(child).getId();
    UUID externalId =
        organizations
            .saveAndFlush(
                Organization.create("York Cloth Ltd", null, OrganizationType.EXTERNAL_PARTNER))
            .getId();
    authenticate(createMember("MANAGER", false), "MANAGER");
    perform(
            put("/api/v1/common/organizations/{id}", childId)
                .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                .param("name", "Hawthorn Leeds Warehouse"))
        .andExpect(status().isForbidden());
    perform(
            put("/api/v1/common/organizations/{id}", externalId)
                .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                .param("name", "York Cloth Works Ltd"))
        .andExpect(status().isForbidden());
    authenticate(admin, "ADMIN");
    perform(
            put("/api/v1/common/organizations/{id}", childId)
                .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
                .param("name", "Hawthorn Leeds Warehouse"))
        .andExpect(status().isOk());
    assertThat(organizations.findByTenantIdAndId(tenant, childId).orElseThrow().getName())
        .isEqualTo("Hawthorn Leeds Warehouse");
    assertThat(organizations.findByTenantIdAndId(tenant, externalId).orElseThrow().getName())
        .isEqualTo("York Cloth Ltd");
    assertNames(ORIGINAL);
  }

  @Test
  void multipleRootsLeaveTheTenantNameUnchangedWhenAnAdminEditsOne() throws Exception {
    organizations.saveAndFlush(
        Organization.create("Willow Cloth Works Ltd", "GB567891234", OrganizationType.WEAVER));
    update(root, RENAMED, "GBP").andExpect(status().isOk());
    assertThat(organizations.findByTenantIdAndId(tenant, root).orElseThrow().getName())
        .isEqualTo(RENAMED);
    assertThat(tenants.findById(tenant).orElseThrow().name()).isEqualTo(ORIGINAL);
  }

  @Test
  void backfillIsRestrictedToItsOwner() {
    for (String role : List.of("fabric_app", "fabric_system")) {
      assertThat(
              systemJdbc.queryForObject(
                  "SELECT has_function_privilege(?,"
                      + " 'common_company.backfill_tenant_company_names()', 'EXECUTE')",
                  Boolean.class,
                  role))
          .isFalse();
    }
  }

  private void grant(UUID member, String resource, String action, DataScope scope) {
    overrides.saveAndFlush(
        PermissionOverride.builder()
            .userId(member)
            .resource(resource)
            .action(action)
            .dataScope(scope)
            .grantedBy(admin)
            .reason("Company profile authorisation test")
            .build());
  }

  private UUID createTenant() {
    UUID id = UUID.randomUUID();
    systemJdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type) VALUES (?, ?, ?, ?,"
            + " 'REGULAR')",
        id,
        "HW-" + id,
        "hawthorn-" + id,
        ORIGINAL);
    return id;
  }

  private UUID createMember(String roleCode, boolean demo) {
    Role role =
        roles
            .findByTenantIdAndRoleCode(tenant, roleCode)
            .orElseGet(
                () -> roles.saveAndFlush(Role.create(roleCode, roleCode, "Company profile test")));
    User user = User.create("Eleanor", "Hughes", root);
    user.setRole(role);
    user.setDemoSeed(demo);
    return users.saveAndFlush(user).getId();
  }

  /**
   * MockMvc runs the request on the test thread, and the request clears the thread's tenant context
   * when it ends. With the security filters off nothing sets it again for the next request, and the
   * repository reads in the assertions would see no rows under RLS. Restore it around each request.
   */
  private ResultActions perform(RequestBuilder request) throws Exception {
    restoreContext();
    try {
      return mvc.perform(request);
    } finally {
      restoreContext();
    }
  }

  private void restoreContext() {
    TenantContext.setCurrentTenantId(tenant);
    if (member != null) {
      TenantContext.setCurrentUserId(member);
    }
  }

  private void authenticate(UUID member, String role) {
    this.member = member;
    TenantContext.setCurrentUserId(member);
    var principal = new AuthenticatedUserContext(member, role, List.of(), null, tenant);
    var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    authentication.setDetails(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private ResultActions update(UUID organisationId, String name, String currency) throws Exception {
    return perform(
        put("/api/v1/common/organizations/{id}", organisationId)
            .with(authentication(SecurityContextHolder.getContext().getAuthentication()))
            .param("name", name)
            .param("taxId", "GB987654321")
            .param("legalName", "Willow Cloth Holdings Ltd")
            .param("preferredCurrency", currency));
  }

  private void assertRootUpdateDenied(String code) throws Exception {
    update(root, RENAMED, "EUR")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(code));
    assertOriginalProfile();
  }

  private void assertOriginalProfile() {
    assertNames(ORIGINAL);
    Organization organisation = organizations.findByTenantIdAndId(tenant, root).orElseThrow();
    assertThat(organisation.getTaxId()).isEqualTo(ORIGINAL_TAX_ID);
    assertThat(organisation.getLegalName()).isEqualTo(ORIGINAL);
    assertThat(organisation.getPreferredCurrency()).isEqualTo("GBP");
  }

  private void assertNames(String name) {
    assertThat(organizations.findByTenantIdAndId(tenant, root).orElseThrow().getName())
        .isEqualTo(name);
    assertThat(tenants.findById(tenant).orElseThrow().name()).isEqualTo(name);
  }

  private void assertMembershipName(String name) throws Exception {
    perform(
            get("/api/v1/auth/memberships")
                .with(authentication(SecurityContextHolder.getContext().getAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].tenantName").value(name));
  }
}
