package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.app.EvaluationSubscriptionService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfigureMockMvc
class MembershipPermissionRevocationIT extends AbstractFabricAppIntegrationTest {

  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  @Autowired private OrganizationRepository organizations;
  @Autowired private DepartmentRepository departments;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionTemplateRepository templates;
  @Autowired private EvaluationSubscriptionService evaluations;
  @Autowired private JwtService jwt;
  @Autowired private MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping routes;

  @Autowired private AddressService addresses;
  @Autowired private ContactService contactPoints;
  @Autowired private UserContactAssignmentService userContactLinks;
  @Autowired private OrganizationContactAssignmentService organisationContactLinks;
  @Autowired private OrganizationAddressRepository organisationAddresses;
  @Autowired private UserWorkLocationRepository workLocations;
  @Autowired private TradingPartnerRepository partners;
  @Autowired private TradingPartnerRegistryRepository partnerRegistries;

  private UUID tenant;
  private UUID userId;
  private UUID departmentId;
  private UUID adminRoleId;
  private UUID organisationId;
  private String adminToken;
  private Role role;
  private Department department;

  @BeforeEach
  void setUp() {
    tenant = UUID.randomUUID();
    systemJdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, demo_mode, status)"
            + " VALUES (?, ?, ?, 'Hawthorn Weaving Ltd', 'REGULAR', true, 'TRIAL')",
        tenant,
        "HW-" + tenant,
        "hawthorn-" + tenant);
    TenantContext.setCurrentTenantId(tenant);
    evaluations.initialisePlayground(tenant);
    var organisation =
        organizations.saveAndFlush(
            Organization.create("Hawthorn Weaving Ltd", "GB123456789", OrganizationType.WEAVER));
    organisationId = organisation.getId();
    department =
        departments.saveAndFlush(
            Department.create(organisation.getId(), "Spinning", "SPINNING", "Spinning"));
    departmentId = department.getId();
    role = roles.saveAndFlush(Role.create("Spinner", "SPINNER", "Spinning team member"));
    User user = User.create("Eleanor", "Hughes", organisation.getId());
    user.setRole(role);
    userId = users.saveAndFlush(user).getId();
    var adminRole =
        roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Tenant administrator"));
    adminRoleId = adminRole.getId();
    User admin = User.create("Alice", "Shaw", organisationId);
    admin.setRole(adminRole);
    adminToken = tokenFor(users.saveAndFlush(admin).getId(), "alice@example.com");
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void theSameTokenLosesAndRegainsADepartmentGrant() throws Exception {
    grant(department.getDepartmentCode());
    assignDepartment(adminToken, userId).andExpect(status().isOk());
    String token = accessToken();
    yarns(token).andExpect(status().isOk());

    TenantContext.setCurrentTenantId(tenant);
    mvc.perform(
            delete(departmentsPath(userId) + "/" + departmentId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    yarns(token).andExpect(status().isForbidden());

    TenantContext.setCurrentTenantId(tenant);
    assignDepartment(adminToken, userId).andExpect(status().isOk());
    yarns(token).andExpect(status().isOk());
  }

  @Test
  void theSameTokenLosesARoleGrantAfterDeactivation() throws Exception {
    grant(null);
    String token = accessToken();
    yarns(token).andExpect(status().isOk());

    TenantContext.setCurrentTenantId(tenant);
    mvc.perform(
            delete("/api/v1/common/roles/" + role.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    yarns(token).andExpect(status().isForbidden());
  }

  @Test
  void anOrdinaryMemberCannotChangeMembershipOrRoles() throws Exception {
    String token = accessToken();
    assignDepartment(token, userId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERS_MANAGE_REQUIRED"));
    var roleBody =
        """
        {"roleName":"Office coordinator","roleCode":"OFFICE_COORDINATOR"}
        """;
    mvc.perform(
            post("/api/v1/common/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/common/roles/" + role.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleBody))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/common/roles/" + role.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/common/roles/" + adminRoleId)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_ADMIN_PROTECTED"));

    TenantContext.setCurrentTenantId(tenant);
    assertThat(users.findByIdWithPermissionData(tenant, userId).orElseThrow().getUserDepartments())
        .isEmpty();
    assertThat(roles.findByTenantIdAndRoleCode(tenant, "OFFICE_COORDINATOR")).isEmpty();
    assertThat(roles.findByTenantIdAndId(tenant, role.getId()).orElseThrow().getRoleName())
        .isEqualTo("Spinner");
    assertThat(roles.findByTenantIdAndId(tenant, adminRoleId).orElseThrow().getIsActive()).isTrue();
  }

  @Test
  void aDelegatedManagerCannotElevateThemselvesOrCreateAnAdministrator() throws Exception {
    grant("members", "manage", DataScope.ORGANIZATION);
    grant("members", "write", DataScope.ORGANIZATION);
    String token = accessToken();
    assignDepartment(token, userId)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERS_SELF_ASSIGNMENT_FORBIDDEN"));
    mvc.perform(
            post("/api/v1/common/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"roleName":"Administrator alias","roleCode":"ROLE_ADMIN"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_CODE_RESERVED"));
    String body =
        """
        {"firstName":"Lucy","lastName":"Bell","contactValue":"lucy@example.com",
         "contactType":"EMAIL","organizationId":"%s","roleId":"%s","invitationEmailSuppressed":true}
        """
            .formatted(organisationId, adminRoleId);
    mvc.perform(
            post("/api/v1/common/users/internal")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERS_ADMIN_GRANT_REQUIRES_ADMIN"));

    TenantContext.setCurrentTenantId(tenant);
    assertThat(roles.findByTenantIdAndRoleCode(tenant, "ROLE_ADMIN")).isEmpty();
    assertThat(users.existsByTenantIdAndContactValue(tenant, "lucy@example.com")).isFalse();
    assertThat(users.findByIdWithPermissionData(tenant, userId).orElseThrow().getUserDepartments())
        .isEmpty();

    mvc.perform(
            post("/api/v1/common/users/internal")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void anOrdinaryMemberCannotAssignAWorkLocation() throws Exception {
    var address =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    organisationAddresses.saveAndFlush(
        OrganizationAddress.builder()
            .organizationId(organisationId)
            .addressId(address.getId())
            .build());
    String token = accessToken();
    mvc.perform(
            post("/api/v1/common/users/" + userId + "/work-locations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgAddressId":"%s","isPrimary":true}
                    """
                        .formatted(address.getId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERS_MANAGE_REQUIRED"));

    TenantContext.setCurrentTenantId(tenant);
    assertThat(workLocations.findByTenantIdAndUserId(tenant, userId)).isEmpty();
  }

  @Test
  void departmentScopedManageCannotAssignAnotherMember() throws Exception {
    grant("members", "manage", DataScope.DEPARTMENT);
    String token = accessToken();
    assignDepartment(token, jwt.getUserIdFromToken(adminToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBERS_MANAGE_REQUIRED"));
  }

  @Test
  void organisationWritesRequireCompanyConfigurationAndProtectTheRoot() throws Exception {
    String worker = accessToken();
    String path = "/api/v1/common/organizations";
    String body =
        """
        {"name":"Leeds Mill","taxId":"GB987654321","organizationType":"WEAVER",
         "parentOrganizationId":"%s"}
        """
            .formatted(organisationId);
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + worker)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_CONFIGURE_REQUIRED"));

    TenantContext.setCurrentTenantId(tenant);
    assertThat(organizations.existsByTenantIdAndTaxId(tenant, "GB987654321")).isFalse();
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    TenantContext.setCurrentTenantId(tenant);
    UUID childId =
        organizations.findByTenantIdAndIsActiveTrue(tenant).stream()
            .filter(org -> "GB987654321".equals(org.getTaxId()))
            .findFirst()
            .orElseThrow()
            .getId();
    String child = path + "/" + childId;

    mvc.perform(
            put(child).header("Authorization", "Bearer " + worker).param("name", "Leeds Warehouse"))
        .andExpect(status().isForbidden());
    mvc.perform(delete(child).header("Authorization", "Bearer " + worker))
        .andExpect(status().isForbidden());

    TenantContext.setCurrentTenantId(tenant);
    assertThat(organizations.findByTenantIdAndId(tenant, childId).orElseThrow().getName())
        .isEqualTo("Leeds Mill");
    assertThat(organizations.findByTenantIdAndId(tenant, childId).orElseThrow().getIsActive())
        .isTrue();
    mvc.perform(
            put(child)
                .header("Authorization", "Bearer " + adminToken)
                .param("name", "Leeds Warehouse"))
        .andExpect(status().isOk());
    mvc.perform(delete(child).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    mvc.perform(delete(path + "/" + organisationId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORG_ROOT_CANNOT_BE_DEACTIVATED"));
  }

  @Test
  void internalAddressAndContactWritesAndImpactRequireCompanyConfiguration() throws Exception {
    var address =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    String path = "/api/v1/common/organizations/" + organisationId + "/addresses";
    String worker = accessToken();
    String body =
        """
        {"addressId":"%s","isPrimary":true}
        """
            .formatted(address.getId());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + worker)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    addOrganisationContact(organisationId, worker).andExpect(status().isForbidden());

    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    addOrganisationContact(organisationId, adminToken).andExpect(status().isOk());
    String impact = path + "/" + address.getId() + "/deletion-impact";
    mvc.perform(get(impact).header("Authorization", "Bearer " + worker))
        .andExpect(status().isForbidden());
    mvc.perform(get(impact).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void aSeededAdministratorCanEditAnAddressInThePlayground() throws Exception {
    var adminRole = roles.findByTenantIdAndId(tenant, adminRoleId).orElseThrow();
    User persona = User.create("Beth", "Reed", organisationId);
    persona.setRole(adminRole);
    persona.setDemoSeed(true);
    UUID personaId = users.saveAndFlush(persona).getId();
    String token = tokenFor(personaId, "beth@example.com");
    var address =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    mvc.perform(
            post("/api/v1/common/organizations/" + organisationId + "/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"addressId":"%s","isPrimary":true}
                    """
                        .formatted(address.getId())))
        .andExpect(status().isOk());
  }

  @Test
  void aSalesGrantCanEditACustomerButNeedsOrganisationScope() throws Exception {
    var customer = partner(PartnerType.CUSTOMER, "Aire Cloth Ltd");
    var supplier = partner(PartnerType.SUPPLIER, "Wharfe Yarn Ltd");
    grant("sales", "write", DataScope.DEPARTMENT);
    String token = accessToken();
    mvc.perform(
            get("/api/v1/common/trading-partners/" + customer.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contactDetailsEdit.allowed").value(false))
        .andExpect(
            jsonPath("$.data.contactDetailsEdit.reason").value("PARTNER_CONTACT_SCOPE_REQUIRED"));
    addOrganisationContact(customer.getOrganizationId(), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PARTNER_CONTACT_SCOPE_REQUIRED"));

    TenantContext.setCurrentTenantId(tenant);
    systemJdbc.update(
        "UPDATE common_user.permission_template SET data_scope = 'ORGANIZATION'"
            + " WHERE tenant_id = ? AND role_code = ? AND resource = 'sales' AND action = 'write'",
        tenant,
        role.getRoleCode());
    mvc.perform(
            get("/api/v1/common/trading-partners/" + customer.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contactDetailsEdit.allowed").value(true));
    addOrganisationContact(customer.getOrganizationId(), token).andExpect(status().isOk());
    addOrganisationContact(supplier.getOrganizationId(), token).andExpect(status().isForbidden());
  }

  private TradingPartner partner(PartnerType type, String name) {
    var organisation =
        organizations.saveAndFlush(
            Organization.create(name, null, OrganizationType.EXTERNAL_PARTNER));
    var registry = TradingPartnerRegistry.create(null, name, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    registry = partnerRegistries.saveAndFlush(registry);
    var partner = TradingPartner.create(registry, type, name);
    partner.setOrganizationId(organisation.getId());
    return partners.saveAndFlush(partner);
  }

  private ResultActions addOrganisationContact(UUID owner, String token) throws Exception {
    return mvc.perform(
        post("/api/v1/common/organizations/" + owner + "/contacts/create-and-assign")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"contactValue":"office@airecloth.example","contactType":"EMAIL"}
                """));
  }

  @Test
  void userContactAndAddressAccessChecksTheTargetAndScope() throws Exception {
    UUID admin = jwt.getUserIdFromToken(adminToken);
    String target = "/api/v1/common/users/" + admin;
    var address =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    String token = accessToken();
    String body =
        """
        {"addressId":"%s","isPrimary":true}
        """
            .formatted(address.getId());
    for (String suffix : new String[] {"/addresses", "/contacts"}) {
      mvc.perform(get(target + suffix).header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }

    mvc.perform(
            post(target + "/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    TenantContext.setCurrentTenantId(tenant);
    grant("members", "write", DataScope.DEPARTMENT);
    mvc.perform(
            post(target + "/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    systemJdbc.update(
        "UPDATE common_user.permission_template SET data_scope = 'ORGANIZATION' WHERE tenant_id = ?"
            + " AND role_code = ? AND resource = 'members' AND action = 'write'",
        tenant,
        role.getRoleCode());
    mvc.perform(
            post(target + "/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/common/users/" + userId + "/contacts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void anOrganisationEditChangesOnlyItsOwnLinkAndKeepsTheUsersVerification() throws Exception {
    var email = contactPoints.createContact("shared@hawthorn.example", ContactType.EMAIL, null);
    contactPoints.verifyContact(email.getId());
    userContactLinks.assignContactForProvisioning(userId, email.getId(), true);
    organisationContactLinks.ensureOnboardingContact(organisationId, email.getId(), false);
    String path = "/api/v1/common/organizations/" + organisationId + "/contacts/" + email.getId();
    editContact(
            path,
            """
            {"label":"Mill office"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.label").value("Mill office"));

    TenantContext.setCurrentTenantId(tenant);
    assertThat(userContactLinks.getUserContacts(userId))
        .singleElement()
        .satisfies(link -> assertThat(link.getLabel()).isEqualTo("Primary"));
    assertThat(contactPoints.findById(email.getId()).orElseThrow().getIsVerified()).isTrue();

    editContact(
            path,
            """
            {"contactValue":"sales@hawthorn.example"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contact.contactValue").value("sales@hawthorn.example"))
        .andExpect(jsonPath("$.data.contact.isVerified").value(false));
    TenantContext.setCurrentTenantId(tenant);
    var original = contactPoints.findById(email.getId()).orElseThrow();
    assertThat(original.getContactValue()).isEqualTo("shared@hawthorn.example");
    assertThat(original.getIsVerified()).isTrue();
    assertThat(userContactLinks.getUserContacts(userId))
        .singleElement()
        .satisfies(link -> assertThat(link.getContactId()).isEqualTo(original.getId()));
  }

  @Test
  void aCompanyEditorCannotAttachAnotherMembersPersonalContactByIdOrValue() throws Exception {
    var email = contactPoints.createContact("eleanor-private@example.com", ContactType.EMAIL, null);
    userContactLinks.assignContactForProvisioning(userId, email.getId(), true);
    grant("organization", "configure", DataScope.ORGANIZATION);
    // An ordinary company editor must lack members:write. Use a separate account from the owner.
    TenantContext.setCurrentTenantId(tenant);
    var editor = User.create("Louise", "Ward", organisationId);
    editor.setRole(role);
    String token = tokenFor(users.saveAndFlush(editor).getId(), "louise@example.com");
    String path = "/api/v1/common/organizations/" + organisationId + "/contacts";
    String byId =
        """
        {"contactId":"%s"}
        """
            .formatted(email.getId());
    for (var route :
        java.util.Map.of(
                path,
                byId,
                path + "/create-and-assign",
                "{\"contactValue\":\"ELEANOR-PRIVATE@example.com\",\"contactType\":\"EMAIL\"}")
            .entrySet()) {
      mvc.perform(
              post(route.getKey())
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(route.getValue()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CONTACT_BELONGS_TO_ANOTHER_USER"));
    }
    TenantContext.setCurrentTenantId(tenant);
    assertThat(organisationContactLinks.getOrganizationContacts(organisationId)).isEmpty();
  }

  @Test
  void editingASharedOrganisationAddressPreservesItsPersonalCopyAndMovesWorkLocations()
      throws Exception {
    var address =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    organisationAddresses.saveAndFlush(
        OrganizationAddress.builder()
            .organizationId(organisationId)
            .addressId(address.getId())
            .isPrimary(true)
            .build());
    UUID admin = jwt.getUserIdFromToken(adminToken);
    mvc.perform(
            post("/api/v1/common/users/" + admin + "/addresses")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"addressId":"%s","isPrimary":true}
                    """
                        .formatted(address.getId())))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/common/users/" + userId + "/work-locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgAddressId":"%s","isPrimary":true}
                    """
                        .formatted(address.getId())))
        .andExpect(status().isOk());

    mvc.perform(
            put("/api/v1/common/organizations/" + organisationId + "/addresses/" + address.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"label":"Leeds mill"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.label").value("Leeds mill"));

    TenantContext.setCurrentTenantId(tenant);
    var replacement =
        organisationAddresses.findPrimaryByOrganizationId(organisationId).orElseThrow();
    assertThat(replacement.getAddressId()).isNotEqualTo(address.getId());
    assertThat(addresses.requireActiveAddress(address.getId()).getLabel()).isEqualTo("Mill office");
    assertThat(workLocations.findByTenantIdAndUserId(tenant, userId))
        .singleElement()
        .satisfies(
            link -> assertThat(link.getOrgAddressId()).isEqualTo(replacement.getAddressId()));
  }

  @Test
  void sharedRemovalPreservesTheContactPointAndTheUserLink() throws Exception {
    var email = contactPoints.createContact("shared@hawthorn.example", ContactType.EMAIL, null);
    userContactLinks.assignContactForProvisioning(userId, email.getId(), true);
    organisationContactLinks.ensureOnboardingContact(organisationId, email.getId(), false);
    mvc.perform(
            delete("/api/v1/common/organizations/" + organisationId + "/contacts/" + email.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    TenantContext.setCurrentTenantId(tenant);
    assertThat(organisationContactLinks.getOrganizationContacts(organisationId)).isEmpty();
    assertThat(userContactLinks.getUserContacts(userId))
        .singleElement()
        .satisfies(link -> assertThat(link.getContactId()).isEqualTo(email.getId()));
    assertThat(contactPoints.findById(email.getId()).orElseThrow().getIsActive()).isTrue();
  }

  @Test
  void replacingAnUnsharedContactValueClearsVerificationOnTheSameRow() throws Exception {
    var email = contactPoints.createContact("old@hawthorn.example", ContactType.EMAIL, null);
    contactPoints.verifyContact(email.getId());
    organisationContactLinks.ensureOnboardingContact(organisationId, email.getId(), false);
    editContact(
            "/api/v1/common/organizations/" + organisationId + "/contacts/" + email.getId(),
            """
            {"contactValue":"new@hawthorn.example"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contact.id").value(email.getId().toString()))
        .andExpect(jsonPath("$.data.contact.isVerified").value(false));
  }

  @Test
  void genericCommunicationWritesAreUnmapped() throws Exception {
    String base = "/api/v1/common";
    UUID id = UUID.randomUUID();
    for (String path :
        java.util.List.of(
            "/addresses",
            "/addresses/validation/validate-and-create",
            "/addresses/validation/" + id + "/revalidate",
            "/contacts")) {
      mvc.perform(post(base + path).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isNotFound());
    }
    for (String path : java.util.List.of("/addresses/" + id, "/contacts/" + id)) {
      mvc.perform(delete(base + path).header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isMethodNotAllowed());
    }
    mvc.perform(put(base + "/addresses/" + id).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(
            put(base + "/contacts/" + id + "/verify")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void everyCoveredWriteRefusesAnUnprivilegedTenantMember() throws Exception {
    UUID otherUser = jwt.getUserIdFromToken(adminToken);
    var sharedAddress =
        addresses.createAddress(
            "18 Mill Lane",
            "Leeds",
            null,
            "LS1 4AB",
            "United Kingdom",
            AddressType.OFFICE,
            "Mill office");
    UUID addressId = sharedAddress.getId();
    organisationAddresses.saveAndFlush(
        OrganizationAddress.builder().organizationId(organisationId).addressId(addressId).build());
    var point =
        contactPoints.createContact("denial-test@hawthorn.example", ContactType.EMAIL, null);
    userContactLinks.assignContactForProvisioning(otherUser, point.getId(), true);
    organisationContactLinks.ensureOnboardingContact(organisationId, point.getId(), false);
    // Seed assignments through the supported administrator routes.
    assignDepartment(adminToken, otherUser).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/common/users/" + otherUser + "/work-locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"orgAddressId\":\"%s\"}").formatted(addressId)))
        .andExpect(status().isOk());
    TenantContext.setCurrentTenantId(tenant);
    var privateAddress =
        addresses.createAddress(
            "24 Mill Lane", "Leeds", null, "LS1 4AB", "United Kingdom", AddressType.HOME, "Home");
    UUID userAddressId = privateAddress.getId();
    mvc.perform(
            post("/api/v1/common/users/" + otherUser + "/addresses")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"addressId\":\"%s\"}").formatted(userAddressId)))
        .andExpect(status().isOk());

    String token = accessToken();
    var controllers =
        Set.of(
            "UserDepartmentController",
            "RoleController",
            "UserWorkLocationController",
            "UserController",
            "OrganizationController",
            "OrganizationAddressController",
            "OrganizationContactController",
            "UserAddressController",
            "UserContactController");
    var authChecked =
        Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
    var ownPreferences =
        Set.of(
            "/api/v1/common/users/me/timezone",
            "/api/v1/common/users/me/language",
            "/api/v1/common/users/me/locale");
    int checked = 0;
    for (var entry : routes.getHandlerMethods().entrySet()) {
      String controller = entry.getValue().getBeanType().getSimpleName();
      if (!controllers.contains(controller)) continue;
      for (String pattern : entry.getKey().getPatternValues()) {
        // Own locale preferences are intentionally writable. They cannot affect another member.
        if (controller.equals("UserController") && ownPreferences.contains(pattern)) continue;
        var methods = entry.getKey().getMethodsCondition().getMethods();
        for (RequestMethod method : methods.isEmpty() ? authChecked : methods) {
          if (!authChecked.contains(method)) continue;
          UUID id =
              switch (controller) {
                case "RoleController" -> role.getId();
                case "OrganizationController" -> organisationId;
                default -> otherUser;
              };
          UUID address = controller.equals("UserAddressController") ? userAddressId : addressId;
          String path =
              pattern
                  .replace("{userId}", otherUser.toString())
                  .replace("{organizationId}", organisationId.toString())
                  .replace("{id}", id.toString())
                  .replace("{departmentId}", departmentId.toString())
                  .replace("{orgAddressId}", addressId.toString())
                  .replace("{addressId}", address.toString())
                  .replace("{contactId}", point.getId().toString());
          String body =
              """
              {"roleName":"Office coordinator","roleCode":"OFFICE_COORDINATOR",
               "firstName":"Lucy","lastName":"Bell","contactValue":"lucy@example.com","contactType":"EMAIL",
               "name":"Aire Cloth Ltd","taxId":"GB987654321","organizationType":"WEAVER","label":"Mill office",
               "streetAddress":"18 Mill Lane","city":"Leeds","country":"United Kingdom","addressType":"OFFICE",
               "addressId":"%s","orgAddressId":"%s","contactId":"%s",
               "organizationId":"%s","departmentId":"%s","parentOrganizationId":"%s"}
              """
                  .formatted(
                      address,
                      addressId,
                      point.getId(),
                      organisationId,
                      departmentId,
                      organisationId);
          mvc.perform(
                  request(HttpMethod.valueOf(method.name()), path)
                      .header("Authorization", "Bearer " + token)
                      .param("name", "Aire Cloth Ltd")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(
                  result ->
                      assertThat(result.getResponse().getStatus())
                          .as("%s %s", method, pattern)
                          .isEqualTo(403));
          checked++;
        }
      }
    }
    assertThat(checked).isGreaterThanOrEqualTo(30);
  }

  @Test
  void foreignOwnerAndRecordIdsAreHiddenUnderRealRowSecurity() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    systemJdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type)"
            + " VALUES (?, ?, ?, 'Aire Cloth Ltd', 'REGULAR')",
        otherTenant,
        "AC-" + otherTenant,
        "aire-" + otherTenant);
    TenantContext.setCurrentTenantId(otherTenant);
    var foreignOrg =
        organizations.saveAndFlush(
            Organization.create("Aire Cloth Ltd", null, OrganizationType.WEAVER));
    UUID foreignUser =
        users.saveAndFlush(User.create("Louise", "Ward", foreignOrg.getId())).getId();
    var foreignDepartment =
        departments.saveAndFlush(
            Department.create(foreignOrg.getId(), "Warehouse", "WAREHOUSE", "Warehouse"));
    var foreignAddress =
        addresses.createAddress(
            "42 Dock Road",
            "York",
            null,
            "YO1 8RS",
            "United Kingdom",
            AddressType.OFFICE,
            "Office");
    var foreignContact =
        contactPoints.createContact("office@airecloth.example", ContactType.EMAIL, null);

    assignDepartment(adminToken, foreignUser).andExpect(status().isNotFound());
    mvc.perform(
            post(departmentsPath(userId))
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"departmentId\":\"%s\"}").formatted(foreignDepartment.getId())))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/common/organizations/" + foreignOrg.getId())
                .header("Authorization", "Bearer " + adminToken)
                .param("name", "Another name"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/common/organizations/" + organisationId + "/addresses")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"addressId\":\"%s\"}").formatted(foreignAddress.getId())))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/common/organizations/" + organisationId + "/contacts")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"contactId\":\"%s\"}").formatted(foreignContact.getId())))
        .andExpect(status().isNotFound());

    TenantContext.setCurrentTenantId(otherTenant);
    assertThat(
            organizations
                .findByTenantIdAndId(otherTenant, foreignOrg.getId())
                .orElseThrow()
                .getName())
        .isEqualTo("Aire Cloth Ltd");
    assertThat(
            users
                .findByIdWithPermissionData(otherTenant, foreignUser)
                .orElseThrow()
                .getUserDepartments())
        .isEmpty();
    assertThat(contactPoints.findById(foreignContact.getId()).orElseThrow().getIsActive()).isTrue();
    assertThat(addresses.requireActiveAddress(foreignAddress.getId()).getStreetAddress())
        .isEqualTo("42 Dock Road");
  }

  private ResultActions editContact(String path, String body) throws Exception {
    return mvc.perform(
        put(path + "/edit")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void grant(String resource, String action, DataScope scope) {
    templates.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(role.getRoleCode())
            .resource(resource)
            .action(action)
            .dataScope(scope)
            .build());
  }

  private ResultActions assignDepartment(String token, UUID target) throws Exception {
    return mvc.perform(
        post(departmentsPath(target))
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"departmentId":"%s","isPrimary":true}
                """
                    .formatted(departmentId)));
  }

  private static String departmentsPath(UUID target) {
    return "/api/v1/common/users/" + target + "/departments";
  }

  private String tokenFor(UUID memberId, String email) {
    User user = users.findByIdWithPermissionData(tenant, memberId).orElseThrow();
    return jwt.generateDemoImpersonationAccessToken(user, null, email);
  }

  private void grant(String departmentCode) {
    templates.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(role.getRoleCode())
            .departmentCode(departmentCode)
            .resource("yarn")
            .action("read")
            .dataScope(DataScope.ORGANIZATION)
            .build());
  }

  private String accessToken() {
    TenantContext.setCurrentTenantId(tenant);
    return tokenFor(userId, "eleanor@example.com");
  }

  private ResultActions yarns(String token) throws Exception {
    TenantContext.clear();
    return mvc.perform(get("/api/v1/production/yarns").header("Authorization", "Bearer " + token));
  }
}
