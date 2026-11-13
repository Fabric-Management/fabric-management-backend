package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Development-only platform operator: one Longward staff account in the platform (system) tenant.
 *
 * <p>Platform staff belong to Longward, the owner of FabricOS, and never to a customer or demo
 * tenant. The account is created only when its e-mail and password are given as environment
 * variables, so no credential lives in the repository. {@link DevSeedDataRunner} runs this only in
 * the {@code dev} and {@code docker} profiles; a live installation creates its platform staff by a
 * separate, deliberate procedure.
 *
 * <ul>
 *   <li>{@value #EMAIL_VARIABLE} — required
 *   <li>{@value #PASSWORD_VARIABLE} — required
 *   <li>{@value #FIRST_NAME_VARIABLE}, {@value #LAST_NAME_VARIABLE} — optional
 * </ul>
 *
 * <p>Use an e-mail address that is used for nothing else in the installation: a login identity is
 * shared by e-mail, so the same address on a customer or demo tenant would join platform staff to
 * that tenant. The account is created once; changing the variables later has no effect until the
 * development database is reset. A failure here is logged and never stops the other seeders.
 */
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
@Slf4j
public class PlatformOperatorDevSeeder implements DataSeeder {

  static final String EMAIL_VARIABLE = "FABRICOS_DEV_PLATFORM_ADMIN_EMAIL";
  static final String PASSWORD_VARIABLE = "FABRICOS_DEV_PLATFORM_ADMIN_PASSWORD";
  static final String FIRST_NAME_VARIABLE = "FABRICOS_DEV_PLATFORM_ADMIN_FIRST_NAME";
  static final String LAST_NAME_VARIABLE = "FABRICOS_DEV_PLATFORM_ADMIN_LAST_NAME";

  static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

  private final Environment environment;
  private final UserCreationService userCreationService;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final OrganizationRepository organizationRepository;
  private final ContactRepository contactRepository;
  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final IdentityProvisioningService identityProvisioningService;
  private final TransactionTemplate transactionTemplate;

  /** The operator account read from the environment. */
  record Operator(String email, String password, String firstName, String lastName) {}

  @Override
  public boolean isSeeded() {
    Optional<Operator> operator = configuredOperator();
    if (operator.isEmpty()) {
      log.info(
          "No development platform operator configured ({} / {} not set); none is created.",
          EMAIL_VARIABLE,
          PASSWORD_VARIABLE);
      return true;
    }
    UUID platformTenant = TenantContext.SYSTEM_TENANT_ID;
    return TenantContext.executeInTenantContext(
        platformTenant,
        () ->
            Boolean.TRUE.equals(
                transactionTemplate.execute(
                    status ->
                        userRepository.existsByTenantIdAndContactValue(
                            platformTenant, operator.get().email()))));
  }

  @Override
  public void seed() {
    Operator operator = configuredOperator().orElseThrow();
    UUID platformTenant = TenantContext.SYSTEM_TENANT_ID;
    try {
      TenantContext.executeInTenantContext(
          platformTenant,
          () ->
              transactionTemplate.executeWithoutResult(
                  status -> createOperator(operator, platformTenant)));
      log.info("Development platform operator created in the platform tenant.");
    } catch (RuntimeException ex) {
      // Optional account: the demo data must still be seeded when this one fails.
      log.error("Could not create the development platform operator: {}", ex.getMessage(), ex);
    }
  }

  private void createOperator(Operator operator, UUID platformTenant) {
    Organization longward =
        organizationRepository.findByTenantIdAndIsActiveTrue(platformTenant).stream()
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("The platform tenant has no active organisation"));
    Role platformAdmin =
        roleRepository
            .findByTenantIdAndRoleCode(platformTenant, PLATFORM_ADMIN_ROLE)
            .orElseThrow(
                () -> new IllegalStateException("The platform tenant has no PLATFORM_ADMIN role"));

    // Created without a role: the ordinary user path refuses platform-scope roles. The role is
    // given here, by trusted provisioning code, and nowhere else.
    UserDto created =
        userCreationService.createInternalUser(
            CreateInternalUserRequest.builder()
                .firstName(operator.firstName())
                .lastName(operator.lastName())
                .contactValue(operator.email())
                .contactType(ContactType.EMAIL)
                .organizationId(longward.getId())
                .invitationEmailSuppressed(true)
                .build());

    User user =
        userRepository
            .findByTenantIdAndId(platformTenant, created.getId())
            .orElseThrow(() -> new IllegalStateException("Platform operator not found"));
    user.setRole(platformAdmin);
    // Platform staff have no company to set up: without this, login sends them to onboarding,
    // which the platform tenant refuses.
    user.completeOnboarding();
    userRepository.save(user);

    contactRepository
        .findByTenantIdAndContactValue(platformTenant, operator.email())
        .ifPresent(
            contact -> {
              contact.verify();
              contactRepository.save(contact);
            });

    String passwordHash = passwordEncoder.encode(operator.password());
    AuthUser authUser = AuthUser.create(user.getId(), passwordHash);
    authUser.setTenantId(platformTenant);
    authUser.verify();
    authUserRepository.save(authUser);
    identityProvisioningService.provisionCredential(
        operator.email(),
        passwordHash,
        false,
        MfaType.NONE,
        null,
        true,
        platformTenant,
        user.getId());
  }

  Optional<Operator> configuredOperator() {
    String email = trimmed(EMAIL_VARIABLE);
    String password = environment.getProperty(PASSWORD_VARIABLE);
    if (email == null || password == null || password.isBlank()) {
      return Optional.empty();
    }
    String firstName = Optional.ofNullable(trimmed(FIRST_NAME_VARIABLE)).orElse("Platform");
    String lastName = Optional.ofNullable(trimmed(LAST_NAME_VARIABLE)).orElse("Operator");
    return Optional.of(new Operator(email.toLowerCase(Locale.ROOT), password, firstName, lastName));
  }

  private String trimmed(String variable) {
    String value = environment.getProperty(variable);
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Runs first: it needs only the platform tenant's migration rows, and a failure here is caught in
   * {@link #seed()}, so it never stops the demo seeders.
   */
  @Override
  public int getOrder() {
    return 5;
  }
}
