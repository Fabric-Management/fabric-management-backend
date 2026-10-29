package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationContactRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserAddress;
import com.fabricmanagement.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.OnboardingJourneyRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fabricmanagement.testsupport.CompletedCompanyOnboardingFixture;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Real PostgreSQL transactions and locks; no mocked persistence or permission decisions. */
// No real server: JwtService needs OrganizationRepository while embedded Tomcat starts, so a spy
// made there lives in Tomcat's class loader and Spring cannot proxy it. The tests call services.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(OnboardingCompletionIT.Observation.class)
class OnboardingCompletionIT extends AbstractIntegrationTest {
  @Autowired private UserOnboardingService onboarding;
  @Autowired private TenantRepository tenants;
  @MockitoSpyBean private OrganizationRepository organizations;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private AddressRepository addressRows;
  @Autowired private ContactRepository contactRows;
  @Autowired private OrganizationAddressRepository organizationAddresses;
  @Autowired private OrganizationContactRepository organizationContacts;
  @Autowired private UserContactRepository personalContacts;
  @Autowired private UserAddressRepository personalAddresses;
  @Autowired private OrganizationAddressAssignmentService addressAssignments;
  @Autowired private OrganizationContactAssignmentService contactAssignments;
  @MockitoSpyBean private AddressService addresses;
  @MockitoSpyBean private ContactService contacts;
  @MockitoSpyBean private UserProfileService profiles;
  @MockitoSpyBean private OnboardingCommandLockService commandLocks;
  @Autowired private CommittedCompletions completions;
  @Autowired private SignupEvidencePort capture;
  @Autowired private OnboardingJourneyRepository journeys;
  @Autowired private PlatformTransactionManager transactions;
  private UUID tenant;
  private UUID root;
  private UUID actor;
  private Role administrator;

  @BeforeEach
  void seed() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tenant =
        tenants.saveAndFlush(Tenant.create("Hawthorn Weaving " + suffix, "HW-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    root =
        organizations
            .saveAndFlush(
                Organization.create(
                    "Hawthorn Weaving Ltd", "GB-" + suffix, OrganizationType.WEAVER))
            .getId();
    administrator = roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Company setup"));
    actor = member("Eleanor", "Hughes");
    authenticate(tenant, actor);
    UUID registrant = member("Oliver", "Reed");
    CompletedCompanyOnboardingFixture.create(tenant, registrant, capture, journeys, transactions);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void identicalReplayKeepsPersistedIdentitiesAndFirstCompletionTimestamp() {
    onboarding.completeOnboarding(actor, full());
    UUID headquarters = headquarters();
    List<UUID> companyContacts = companyContactIds();
    List<UUID> personal = personalContactIds(actor);
    Instant completedAt = savedActor().getOnboardingCompletedAt();
    onboarding.completeOnboarding(actor, full());
    assertCompleteRows(1);
    assertThat(headquarters()).isEqualTo(headquarters);
    assertThat(companyContactIds()).containsExactlyInAnyOrderElementsOf(companyContacts);
    assertThat(personalContactIds(actor)).containsExactlyInAnyOrderElementsOf(personal);
    assertThat(savedActor().getOnboardingCompletedAt()).isEqualTo(completedAt);
    assertThat(completions.forUser(actor)).hasSize(1);
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.LANDLINE))
        .extracting(Contact::getContactValue)
        .containsExactly("+442079460123");
    assertThat(organizationContacts.findWithContactByTenantIdAndOrganizationId(tenant, root))
        .extracting(assignment -> assignment.getContact().getContactType())
        .containsExactlyInAnyOrder(ContactType.EMAIL, ContactType.LANDLINE);
  }

  @Test
  void concurrentIdenticalCommandsForOneUserCommitOnce() throws Exception {
    concurrent(actor, actor, full());
    assertCompleteRows(1);
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void differentAdministratorsAlsoShareOneCompanyResourceSet() throws Exception {
    UUID second = member("Oliver", "Reed");
    concurrent(actor, second, full());
    assertCompleteRows(2);
    assertThat(personalContactIds(second))
        .containsExactlyInAnyOrderElementsOf(personalContactIds(actor));
    assertThat(users.findByTenantIdAndId(tenant, second).orElseThrow().hasCompletedOnboarding())
        .isTrue();
    assertThat(completions.forUser(actor)).hasSize(1);
    assertThat(completions.forUser(second)).hasSize(1);
  }

  @Test
  void personalOnlyConcurrentLegacyCompletionNeedsNoRootOrganisation() throws Exception {
    // Root is irrelevant to the legacy path; an external partner is excluded from root lookup.
    Organization company = organizations.findById(root).orElseThrow();
    company.setOrganizationType(OrganizationType.EXTERNAL_PARTNER);
    organizations.saveAndFlush(company);
    concurrent(actor, actor, null);
    assertThat(savedActor().hasCompletedOnboarding()).isTrue();
    assertThat(organizationAddresses.findByTenantIdAndOrganizationId(tenant, root)).isEmpty();
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void salesLedHeadquartersAndNormalisedContactsAreReused() {
    Address existing =
        addresses.createAddress(
            CreateAddressRequest.builder()
                .streetAddress("14 Mill Street")
                .city("Leeds")
                .country("United Kingdom")
                .addressType(AddressType.HEADQUARTERS)
                .label("Mill office")
                .build());
    addressAssignments.assignAddress(root, existing.getId(), true, true);
    Contact email =
        contacts.createContact("OFFICE@HAWTHORN.EXAMPLE", ContactType.EMAIL, "Office", false, null);
    contactAssignments.assignContact(root, email.getId(), true, null);
    onboarding.completeOnboarding(actor, full());
    assertCompleteRows(1);
    assertThat(headquarters()).isEqualTo(existing.getId());
    assertThat(companyContactIds()).contains(email.getId());
    assertThat(addressRows.findById(existing.getId()).orElseThrow().getPostalCode())
        .isEqualTo("LS1 4AB");
  }

  @Test
  void authorisedLaterCorrectionPreservesOmittedDataAndDoesNotCompleteAgain() {
    onboarding.completeOnboarding(actor, full());
    UUID address = headquarters();
    var timestamp = savedActor().getOnboardingCompletedAt();
    onboarding.completeOnboarding(
        actor,
        CompleteOnboardingRequest.builder()
            .legalName("")
            .description("")
            .addressLine2("")
            .city("York")
            .build());
    Organization saved = organizations.findById(root).orElseThrow();
    assertThat(saved.getLegalName()).isNull();
    assertThat(saved.getDescription()).isNull();
    assertThat(saved.getWebsite()).isEqualTo("https://hawthorn.example.com");
    Address hq = addressRows.findById(address).orElseThrow();
    assertThat(hq.getAddressLine2()).isNull();
    assertThat(hq.getCity()).isEqualTo("York");
    assertThat(hq.getCountry()).isEqualTo("United Kingdom");
    assertThat(headquarters()).isEqualTo(address);
    assertThat(savedActor().getOnboardingCompletedAt()).isEqualTo(timestamp);
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void nullAndOmittedFieldsLeaveExistingDataAndAddressVerificationUnchanged() {
    onboarding.completeOnboarding(actor, full());
    UUID address = headquarters();
    Address before = addressRows.findById(address).orElseThrow();
    var request =
        CompleteOnboardingRequest.builder()
            .legalName(null)
            .city(null)
            .description(null)
            .organizationName("Hawthorn Cloth Ltd")
            .build();
    onboarding.completeOnboarding(actor, request);
    Organization company = organizations.findById(root).orElseThrow();
    assertThat(company.getLegalName()).isEqualTo("Hawthorn Cloth Limited");
    assertThat(company.getDescription()).isEqualTo("British woven cloth");
    Address after = addressRows.findById(address).orElseThrow();
    assertThat(after.getCity()).isEqualTo("Leeds");
    assertThat(after.getVerifiedAt()).isEqualTo(before.getVerifiedAt());
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void partialNewHeadquartersFailsBeforeAnyPersonalOrCompanyWrite() {
    var request =
        CompleteOnboardingRequest.builder().city("Leeds").mobilePhone("+447700900123").build();
    assertCode(
        () -> onboarding.completeOnboarding(actor, request), "ONBOARDING_HQ_ADDRESS_INCOMPLETE");
    assertUntouched();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "organizationName",
        "taxId",
        "addressLine1",
        "city",
        "country",
        "addressLabel",
        "companyEmail",
        "companyPhone",
        "mobilePhone"
      })
  void invalidMixedProfileCannotLeaveRowsBehind(String field) throws Exception {
    var request = full();
    var property = CompleteOnboardingRequest.class.getDeclaredField(field);
    property.setAccessible(true);
    property.set(request, "   ");
    assertCode(() -> onboarding.completeOnboarding(actor, request), "ONBOARDING_PROFILE_INVALID");
    assertUntouched();
  }

  @ParameterizedTest
  @ValueSource(strings = {"organisation", "personal-mobile", "headquarters", "company-contact"})
  void failureAtAnyWriteStageRollsBackAndRetryStartsCleanly(String stage) {
    switch (stage) {
      case "organisation" ->
          doAnswer(
                  invocation -> {
                    // A repository spy wraps Spring Data's proxy of an interface: there is no real
                    // method to call, so the spy's own delegating answer performs the save.
                    mockingDetails(organizations)
                        .getMockCreationSettings()
                        .getDefaultAnswer()
                        .answer(invocation);
                    organizations.flush();
                    throw injected();
                  })
              .when(organizations)
              .save(any(Organization.class));
      case "personal-mobile" ->
          doAnswer(
                  invocation -> {
                    invocation.callRealMethod();
                    throw injected();
                  })
              .when(profiles)
              .updatePersonalContact(any(), any(), any());
      case "headquarters" ->
          doAnswer(
                  invocation -> {
                    invocation.callRealMethod();
                    throw injected();
                  })
              .when(addresses)
              .createAddress(any(CreateAddressRequest.class));
      case "company-contact" ->
          doAnswer(
                  invocation -> {
                    invocation.callRealMethod();
                    throw injected();
                  })
              .when(contacts)
              .reuseOrCreateOnboardingContact(any());
      default -> throw new AssertionError(stage);
    }
    assertCode(() -> onboarding.completeOnboarding(actor, full()), "ONBOARDING_INJECTED_FAILURE");
    assertUntouched();
    reset(organizations, addresses, profiles, contacts);
    onboarding.completeOnboarding(actor, full());
    onboarding.completeOnboarding(actor, full());
    assertCompleteRows(1);
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void completionEventFailureAlsoRollsBackTheEntireProfile() {
    completions.rejectFor(actor);
    try {
      assertCode(() -> onboarding.completeOnboarding(actor, full()), "ONBOARDING_INJECTED_FAILURE");
      assertUntouched();
    } finally {
      completions.stopRejecting(actor);
    }
    onboarding.completeOnboarding(actor, full());
    assertCompleteRows(1);
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @Test
  void failedCorrectionOfAnAlreadyCompletedProfileRetainsTheOriginalAddressAndMarker() {
    onboarding.completeOnboarding(actor, full());
    UUID address = headquarters();
    Instant completedAt = savedActor().getOnboardingCompletedAt();
    List<UUID> existingContacts = companyContactIds();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              contactRows.flush();
              throw injected();
            })
        .when(contacts)
        .reuseOrCreateOnboardingContact(any());
    var correction =
        CompleteOnboardingRequest.builder()
            .organizationName("Willow Cloth Ltd")
            .city("York")
            .companyEmail("office@willow.example")
            .build();
    assertCode(
        () -> onboarding.completeOnboarding(actor, correction), "ONBOARDING_INJECTED_FAILURE");
    assertThat(organizations.findById(root).orElseThrow().getName())
        .isEqualTo("Hawthorn Cloth Ltd");
    assertThat(addressRows.findById(address).orElseThrow().getCity()).isEqualTo("Leeds");
    assertThat(companyContactIds()).containsExactlyInAnyOrderElementsOf(existingContacts);
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.EMAIL)).hasSize(1);
    assertThat(savedActor().getOnboardingCompletedAt()).isEqualTo(completedAt);
    assertThat(completions.forUser(actor)).hasSize(1);
    reset(contacts);
    onboarding.completeOnboarding(actor, correction);
    assertThat(addressRows.findById(address).orElseThrow().getCity()).isEqualTo("York");
    assertThat(completions.forUser(actor)).hasSize(1);
  }

  @ParameterizedTest
  // Only a personal card can share the address: uk_organization_address_address_id lets an
  // address belong to one organisation at most, so another organisation can never hold it.
  @ValueSource(strings = {"personal"})
  void anotherOwnersSharedHeadquartersIsNeverOverwritten(String owner) {
    Address shared =
        addresses.createAddress(
            CreateAddressRequest.builder()
                .streetAddress("9 Bridge Road")
                .city("York")
                .country("United Kingdom")
                .addressType(AddressType.HEADQUARTERS)
                .label("Shared office")
                .build());
    addressAssignments.assignAddress(root, shared.getId(), true, true);
    if (owner.equals("organisation")) {
      Organization branch =
          Organization.create("Hawthorn York Mill", "GB987654321", OrganizationType.WEAVER);
      branch.setParentOrganizationId(root);
      branch = organizations.saveAndFlush(branch);
      addressAssignments.assignAddress(branch.getId(), shared.getId(), true, true);
    } else {
      personalAddresses.saveAndFlush(
          UserAddress.builder().userId(actor).addressId(shared.getId()).build());
    }
    assertCode(() -> onboarding.completeOnboarding(actor, full()), "ONBOARDING_HQ_ADDRESS_SHARED");
    assertThat(addressRows.findById(shared.getId()).orElseThrow().getStreetAddress())
        .isEqualTo("9 Bridge Road");
    assertThat(savedActor().hasCompletedOnboarding()).isFalse();
    assertThat(personalContactIds(actor)).isEmpty();
    assertThat(completions.forUser(actor)).isEmpty();
  }

  @Test
  void taxConflictCannotPartiallySaveMobileOrCompletion() {
    Organization branch =
        Organization.create("Hawthorn York Mill", "GB987654321", OrganizationType.WEAVER);
    branch.setParentOrganizationId(root);
    organizations.saveAndFlush(branch);
    var request = full();
    request.setTaxId("GB987654321");
    assertCode(() -> onboarding.completeOnboarding(actor, request), "TAX_ID_ALREADY_EXISTS");
    assertUntouched();
  }

  @Test
  void missingRootIsAnErrorBeforePersonalWrites() {
    Organization company = organizations.findById(root).orElseThrow();
    company.setOrganizationType(OrganizationType.EXTERNAL_PARTNER);
    organizations.saveAndFlush(company);
    assertCode(
        () -> onboarding.completeOnboarding(actor, full()),
        "ONBOARDING_ROOT_ORGANIZATION_NOT_FOUND");
    assertUntouched();
  }

  @Test
  void otherTenantCannotUseThisActorOrMutateTheirCompany() {
    UUID other =
        tenants
            .saveAndFlush(
                Tenant.create(
                    "Willow Cloth " + UUID.randomUUID().toString().substring(0, 8),
                    "WC-" + UUID.randomUUID()))
            .getId();
    authenticate(other, actor);
    try {
      assertCode(
          () -> onboarding.completeOnboarding(actor, full()), "ONBOARDING_MEMBERSHIP_REQUIRED");
    } finally {
      authenticate(tenant, actor);
    }
    assertUntouched();
  }

  @Test
  void identicalContactValueInAnotherTenantIsNeverBorrowed() {
    UUID otherTenant =
        tenants
            .saveAndFlush(
                Tenant.create(
                    "Willow Cloth " + UUID.randomUUID().toString().substring(0, 8),
                    "WC-" + UUID.randomUUID()))
            .getId();
    authenticate(otherTenant, actor);
    UUID otherContact;
    try {
      otherContact =
          contacts
              .createContact(
                  "office@hawthorn.example", ContactType.EMAIL, "Willow office", false, null)
              .getId();
    } finally {
      authenticate(tenant, actor);
    }
    onboarding.completeOnboarding(actor, full());
    assertCompleteRows(1);
    assertThat(companyContactIds()).doesNotContain(otherContact);
  }

  private void concurrent(UUID first, UUID second, CompleteOnboardingRequest request)
      throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var bothAuthorisedBeforeLock = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              bothAuthorisedBeforeLock.countDown();
              if (!bothAuthorisedBeforeLock.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(
                    "Both commands must read the pre-completion state before locking");
              }
              return invocation.callRealMethod();
            })
        // The spy behind the transaction proxy: lockAndRefresh is MANDATORY, so stubbing through
        // the
        // proxy would fail for want of a transaction and leave the stubbing unfinished.
        .when(AopTestUtils.<OnboardingCommandLockService>getUltimateTargetObject(commandLocks))
        .lockAndRefresh(any(User.class));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of(first, second).stream()
              .map(
                  user ->
                      pool.submit(
                          () -> {
                            authenticate(tenant, user);
                            try {
                              ready.countDown();
                              if (!start.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("Concurrent start timed out");
                              onboarding.completeOnboarding(user, request);
                              return true;
                            } finally {
                              clear();
                            }
                          }))
              .toList();
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      for (var task : tasks) assertThat(task.get(30, TimeUnit.SECONDS)).isTrue();
    }
  }

  private void assertCompleteRows(int personalAssignmentCount) {
    assertThat(organizationAddresses.findByTenantIdAndOrganizationId(tenant, root)).hasSize(1);
    assertThat(addressRows.findByTenantIdAndAddressType(tenant, AddressType.HEADQUARTERS))
        .hasSize(1);
    assertThat(companyContactIds()).hasSize(2);
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.EMAIL)).hasSize(1);
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.LANDLINE)).hasSize(1);
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.MOBILE)).hasSize(1);
    assertThat(personalContactIds(actor)).hasSize(1);
    assertThat(savedActor().hasCompletedOnboarding()).isTrue();
    if (personalAssignmentCount == 2) {
      assertThat(completions.all().stream().filter(event -> event.getTenantId().equals(tenant)))
          .hasSize(2);
    }
  }

  private void assertUntouched() {
    assertThat(organizations.findById(root).orElseThrow().getName())
        .isEqualTo("Hawthorn Weaving Ltd");
    assertThat(savedActor().hasCompletedOnboarding()).isFalse();
    assertThat(organizationAddresses.findByTenantIdAndOrganizationId(tenant, root)).isEmpty();
    assertThat(companyContactIds()).isEmpty();
    assertThat(personalContactIds(actor)).isEmpty();
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.EMAIL)).isEmpty();
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.MOBILE)).isEmpty();
    assertThat(contactRows.findByTenantIdAndContactType(tenant, ContactType.LANDLINE)).isEmpty();
    assertThat(addressRows.findByTenantIdAndAddressType(tenant, AddressType.HEADQUARTERS))
        .isEmpty();
    assertThat(completions.forUser(actor)).isEmpty();
  }

  private List<UUID> companyContactIds() {
    return organizationContacts.findByTenantIdAndOrganizationId(tenant, root).stream()
        .map(assignment -> assignment.getContactId())
        .toList();
  }

  private List<UUID> personalContactIds(UUID user) {
    return personalContacts.findByTenantIdAndUserId(tenant, user).stream()
        .map(assignment -> assignment.getContactId())
        .toList();
  }

  private UUID headquarters() {
    return organizationAddresses
        .findHeadquartersByOrganizationId(root)
        .orElseThrow()
        .getAddressId();
  }

  private User savedActor() {
    return users.findByTenantIdAndId(tenant, actor).orElseThrow();
  }

  private UUID member(String first, String last) {
    User user = User.create(first, last, root);
    user.setRole(administrator);
    return users.saveAndFlush(user).getId();
  }

  private void authenticate(UUID tenantId, UUID user) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(user);
    var identity = new AuthenticatedUserContext(user, "ADMIN", List.of(), null, tenantId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
  }

  private void assertCode(Runnable command, String code) {
    assertThatThrownBy(command::run)
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(code));
  }

  private static PlatformDomainException injected() {
    return new PlatformDomainException(
        "Injected onboarding persistence failure", "ONBOARDING_INJECTED_FAILURE", 503);
  }

  private CompleteOnboardingRequest full() {
    return CompleteOnboardingRequest.builder()
        .organizationName("Hawthorn Cloth Ltd")
        .legalName("Hawthorn Cloth Limited")
        .website("https://hawthorn.example.com")
        .description("British woven cloth")
        .addressLine1("14 Mill Street")
        .addressLine2("First floor")
        .city("Leeds")
        .country("United Kingdom")
        .postalCode("LS1 4AB")
        .addressLabel("Mill office")
        .companyEmail("office@hawthorn.example")
        .companyPhone("+44 20 7946 0123")
        .mobilePhone("+447700900123")
        .build();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Observation {
    @Bean
    CommittedCompletions committedCompletions() {
      return new CommittedCompletions();
    }
  }

  /**
   * Spring proxies this bean (its listeners are advised), and a proxy's own fields are empty, so
   * the test reaches the state only through methods.
   */
  static class CommittedCompletions {
    private final java.util.Set<UUID> reject = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, List<UserOnboardingCompletedEvent>> events =
        new ConcurrentHashMap<>();

    public void rejectFor(UUID user) {
      reject.add(user);
    }

    public void stopRejecting(UUID user) {
      reject.remove(user);
    }

    public List<UserOnboardingCompletedEvent> all() {
      return events.values().stream().flatMap(List::stream).toList();
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void rejectBeforeCommit(UserOnboardingCompletedEvent event) {
      if (reject.contains(event.getUserId())) throw injected();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(UserOnboardingCompletedEvent event) {
      events.computeIfAbsent(event.getUserId(), ignored -> new CopyOnWriteArrayList<>()).add(event);
    }

    public List<UserOnboardingCompletedEvent> forUser(UUID user) {
      return events.getOrDefault(user, List.of());
    }
  }
}
