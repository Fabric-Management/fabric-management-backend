package com.fabricmanagement.platform.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.RestAuthenticationEntryPoint;
import com.fabricmanagement.common.infrastructure.security.SecurityConfig;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.web.LocalizationFilter;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.CompanyOnboardingProfileService;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.app.OnboardingCommandLockService;
import com.fabricmanagement.platform.user.app.OnboardingRequestValidator;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.app.UserOnboardingService;
import com.fabricmanagement.platform.user.app.UserProfileService;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.security.OnboardingAccessService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Production HTTP security + real facade, command service, access policy and permission evaluator.
 */
@WebMvcTest(
    controllers = UserOnboardingController.class,
    properties = {"logging.file.name=target/onboarding-security-test.log"})
@ActiveProfiles("prod")
@Import({
  SecurityConfig.class, RestAuthenticationEntryPoint.class, JwtAuthenticationFilter.class,
  LocalizationFilter.class, UserService.class, UserOnboardingService.class,
  OnboardingAccessService.class, PermissionEvaluator.class, AuthenticatedUserContextResolver.class,
  OnboardingRequestValidator.class
})
class UserOnboardingControllerSecurityTest {
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final String COMPLETE = "/api/v1/common/users/me/onboarding/complete";
  private static final String STATUS = "/api/v1/common/users/me/onboarding-status";

  @Autowired private MockMvc mvc;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private TenantQueryPort tenantQueryPort;
  @MockitoBean private UserRepository users;
  @MockitoBean private PermissionTemplateRepository templates;
  @MockitoBean private PermissionOverrideRepository overrides;
  @MockitoBean private DepartmentRepository departments;
  @MockitoBean private UserCreationService creation;
  @MockitoBean private UserQueryService queries;
  @MockitoBean private UserProfileService profiles;
  @MockitoBean private UserDepartmentRepository userDepartments;
  @MockitoBean private EmployeeProjectionPort employees;
  @MockitoBean private DomainEventPublisher events;
  @MockitoBean private CompanyOnboardingProfileService companyProfiles;
  @MockitoBean private OnboardingCommandLockService locks;

  @MockitoBean
  private com.fabricmanagement.platform.user.app.OnboardingLegacyRetentionService legacyRetention;

  @MockitoBean private OrganizationService organisations;
  @MockitoBean private OrganizationAddressAssignmentService addresses;
  @MockitoBean private OrganizationContactAssignmentService contacts;
  @MockitoBean private AddressService addressService;
  @MockitoBean private ContactService contactService;

  private User member;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(USER);
    member = User.create("Eleanor", "Hughes", ORGANIZATION);
    member.setId(USER);
    member.setTenantId(TENANT);
    member.setRole(Role.create("Warehouse worker", "WORKER", "Warehouse operations"));
    when(users.findByIdWithPermissionData(TENANT, USER)).thenReturn(Optional.of(member));
    when(users.save(member)).thenReturn(member);
    when(employees.findByUserId(TENANT, USER)).thenReturn(Optional.empty());
    when(templates.findEffectiveTemplatesForDepartments(
            TENANT, "WORKER", java.util.Arrays.asList((String) null)))
        .thenReturn(List.of());
    when(overrides.findActiveOverrides(TENANT, USER)).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  static Stream<String> companyBodies() {
    return Stream.of(
        "{\"organizationName\":\"Hawthorn Weaving Ltd\"}",
        "{\"organizationName\":\"\"}",
        "{\"taxId\":\"     \"}",
        "{\"organizationType\":\"WEAVER\"}",
        "{\"legalName\":\"Hawthorn Weaving Ltd\"}",
        "{\"industry\":\"TEXTILE\"}",
        "{\"addressLine1\":\"14 Mill Street\"}",
        "{\"addressLine2\":\"\"}",
        "{\"companyEmail\":\"accounts@example.com\"}",
        "{\"companyPhone\":\"+442079460123\"}",
        "{\"selectedOS\":[]}",
        "{\"organizationName\":\"Hawthorn Weaving Ltd\",\"mobilePhone\":\"+447700900123\"}");
  }

  @ParameterizedTest
  @MethodSource("companyBodies")
  void companyFieldsAreForbiddenBeforeAnyWrite(String body) throws Exception {
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ONBOARDING_COMPANY_PERMISSION_REQUIRED"));
    assertNoWrites();
    assertThat(member.getOnboardingCompletedAt()).isNull();
  }

  @Test
  void completedMemberCannotUseTheRepeatBranchToChangeTheCompany() throws Exception {
    member.completeOnboarding();
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationName\":\"Hawthorn Weaving Ltd\"}"))
        .andExpect(status().isForbidden());
    assertNoWrites();
  }

  @Test
  void personalMobileAndNoBodyRemainSelfService() throws Exception {
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobilePhone\":\"+447700900123\"}"))
        .andExpect(status().isOk());
    verify(profiles).updatePersonalContact(USER, "+447700900123", ContactType.MOBILE);
    verifyNoInteractions(
        companyProfiles, organisations, addresses, contacts, addressService, contactService);
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(USER);
    mvc.perform(post(COMPLETE).with(caller(TENANT, USER))).andExpect(status().isOk());
  }

  @Test
  void anonymousCannotCompleteOrReadStatus() throws Exception {
    mvc.perform(post(COMPLETE)).andExpect(status().isUnauthorized());
    mvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
    assertNoWrites();
  }

  @Test
  void realOwnerCanUseTheExistingFullCompanyPayload() throws Exception {
    member.setRole(Role.create("Company administrator", "ADMIN", "Company owner"));
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
            {"organizationName":"Hawthorn Weaving Ltd","taxId":"GB123456789",
             "organizationType":"WEAVER","legalName":"Hawthorn Weaving Ltd",
             "industry":"TEXTILE","selectedOS":["FabricOS"]}
            """))
        .andExpect(status().isOk());
    verify(companyProfiles).prepare(any());
    assertThat(member.getOnboardingCompletedAt()).isNotNull();
  }

  @Test
  void delegatedAdministratorNeedsOrganisationScopeAndFreshOverridesWin() throws Exception {
    PermissionOverride grant =
        PermissionOverride.builder()
            .resource("organization")
            .action("configure")
            .dataScope(DataScope.DEPARTMENT)
            .build();
    when(overrides.findActiveOverrides(TENANT, USER)).thenReturn(List.of(grant));
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOS\":[]}"))
        .andExpect(status().isForbidden());
    assertNoWrites();

    grant.setDataScope(DataScope.ORGANIZATION);
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(USER);
    mvc.perform(get(STATUS).with(caller(TENANT, USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companySetup.action").value("organization:configure"))
        .andExpect(jsonPath("$.data.companySetup.allowed").value(true))
        .andExpect(jsonPath("$.data.companySetup.needsApproval").value(false));

    grant.setDataScope(null);
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(USER);
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOS\":[]}"))
        .andExpect(status().isForbidden());
    assertNoWrites();
  }

  @Test
  void seededAdminCannotConfigureTheRealCompany() throws Exception {
    member.setRole(Role.create("Demo administrator", "ADMIN", "Seeded persona"));
    member.setDemoSeed(true);
    mvc.perform(
            post(COMPLETE)
                .with(caller(TENANT, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOS\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ONBOARDING_COMPANY_REQUIRES_REAL_MEMBER"));
    assertNoWrites();
  }

  @Test
  void differentTenantPrincipalCannotTargetTheCurrentCompany() throws Exception {
    mvc.perform(
            post(COMPLETE)
                .with(caller(UUID.randomUUID(), USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationName\":\"Hawthorn Weaving Ltd\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ONBOARDING_IDENTITY_MISMATCH"));
    assertNoWrites();
  }

  @Test
  void staleAdminClaimDoesNotOverrideThePersistedWorkerRole() throws Exception {
    // caller() intentionally claims ADMIN; the current persisted user remains WORKER.
    mvc.perform(get(STATUS).with(caller(TENANT, USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.companySetup.allowed").value(false))
        .andExpect(
            jsonPath("$.data.companySetup.reason").value("ONBOARDING_COMPANY_PERMISSION_REQUIRED"));
  }

  @Test
  void inactiveMemberCannotCompleteEvenPersonalOnboarding() throws Exception {
    member.setIsActive(false);
    mvc.perform(post(COMPLETE).with(caller(TENANT, USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ONBOARDING_MEMBERSHIP_REQUIRED"));
    assertNoWrites();
  }

  @Test
  void anotherCurrentUserCannotBeTargetedThroughTheFacade() throws Exception {
    mvc.perform(post(COMPLETE).with(caller(TENANT, UUID.randomUUID())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ONBOARDING_IDENTITY_MISMATCH"));
    assertNoWrites();
  }

  private RequestPostProcessor caller(UUID tenant, UUID user) {
    var context = new AuthenticatedUserContext(user, "ADMIN", List.of(), null, tenant);
    return authentication(new UsernamePasswordAuthenticationToken(context, null, List.of()));
  }

  private void assertNoWrites() {
    verify(users, never()).save(any());
    verifyNoInteractions(
        profiles,
        companyProfiles,
        organisations,
        addresses,
        contacts,
        addressService,
        contactService,
        events);
  }
}
