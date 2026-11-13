package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.web.exception.ContactAlreadyRegisteredException;
import com.fabricmanagement.common.infrastructure.web.exception.SignupAlreadyPendingException;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * TD-17: a public signup was accepted twice with the same company tax number and the same e-mail,
 * creating two tenants. The duplicate checks ran as the (unauthenticated) caller, whose queries
 * row-level security narrows to the platform tenant, so they always answered "not found".
 *
 * <p>Runs against the real {@code fabric_app} role: on the superuser connection of {@code
 * AbstractIntegrationTest} row-level security is bypassed and the defect cannot be seen. The
 * existing company and e-mail identity are written by the owner connection (as an earlier signup
 * would have left them); the service must then refuse the second signup.
 */
class PublicSignupDuplicateRealRolesIT extends AbstractFabricAppIntegrationTest {

  @Autowired private TenantOnboardingService onboarding;
  @Autowired private OrganizationFacade organizations;
  @Autowired private IdentityProvisioningService identities;
  @Autowired private TenantQueryPort tenantQueryPort;

  private final List<UUID> createdTenants = new ArrayList<>();
  private final List<String> createdEmails = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    // The container is shared with other real-role ITs: leave nothing behind.
    JdbcTemplate owner = owner();
    for (String email : createdEmails) {
      owner.update("DELETE FROM common_auth.login_identity WHERE email = ?", email);
    }
    for (UUID tenantId : createdTenants) {
      owner.update(
          "DELETE FROM common_auth.common_registration_token WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_company.common_organization WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
    }
    createdEmails.clear();
    createdTenants.clear();
  }

  @Test
  @DisplayName("a tax number registered in another tenant is seen without any tenant context")
  void taxIdIsFoundAcrossTenants() {
    String taxId = "TAX-" + shortId();
    UUID tenant = insertTenant();
    insertOrganisation(tenant, taxId);

    // A public request carries no tenant: the lookup falls back to the platform tenant.
    TenantContext.clear();

    assertThat(organizations.existsByTaxId(taxId)).isTrue();
    assertThat(organizations.existsByTaxId("TAX-" + shortId())).isFalse();
    assertThat(organizations.existsByTaxId("  ")).isFalse();
  }

  @Test
  @DisplayName("an e-mail with a login identity is registered, whatever its letter case")
  void emailIsFoundWhateverTheCase() {
    String email = insertLoginIdentity();

    assertThat(identities.isEmailRegistered(email)).isTrue();
    assertThat(identities.isEmailRegistered("  " + email.toUpperCase(Locale.ROOT) + " ")).isTrue();
    assertThat(identities.isEmailRegistered("nobody-" + shortId() + "@example.com")).isFalse();
  }

  @Test
  @DisplayName("the same company cannot sign up twice")
  void secondSignupWithTheSameTaxIdIsRefused() {
    String taxId = "TAX-" + shortId();
    UUID tenant = insertTenant();
    insertOrganisation(tenant, taxId);
    TenantContext.clear();

    assertThatThrownBy(
            () -> onboarding.createSelfServiceTenant(request(taxId, freshEmail("owner"))))
        .isInstanceOf(TaxIdAlreadyExistsException.class);
  }

  @Test
  @DisplayName("the same e-mail cannot sign up twice, even with a different tax number")
  void secondSignupWithTheSameEmailIsRefused() {
    String email = insertLoginIdentity();
    TenantContext.clear();

    assertThatThrownBy(() -> onboarding.createSelfServiceTenant(request("TAX-" + shortId(), email)))
        .isInstanceOf(ContactAlreadyRegisteredException.class);
  }

  @Test
  @DisplayName("an e-mail whose setup link is still waiting cannot sign up again")
  void secondSignupWhileTheFirstIsStillPendingIsRefused() {
    // The admin's login identity exists only after the link is used; the token holds the address.
    String email = freshEmail("pending");
    insertRegistrationToken(insertTenant(), email, false, "now() + interval '1 day'");
    TenantContext.clear();

    assertThat(tenantQueryPort.hasPendingRegistrationFor(email)).isTrue();
    assertThat(tenantQueryPort.hasPendingRegistrationFor("  " + email.toUpperCase(Locale.ROOT)))
        .isTrue();
    assertThatThrownBy(() -> onboarding.createSelfServiceTenant(request("TAX-" + shortId(), email)))
        .isInstanceOf(SignupAlreadyPendingException.class);
  }

  @Test
  @DisplayName("a used or expired setup link no longer holds the e-mail")
  void aUsedOrExpiredTokenDoesNotHoldTheEmail() {
    UUID tenant = insertTenant();
    String used = freshEmail("used");
    String expired = freshEmail("expired");
    insertRegistrationToken(tenant, used, true, "now() + interval '1 day'");
    insertRegistrationToken(tenant, expired, false, "now() - interval '1 hour'");
    TenantContext.clear();

    assertThat(tenantQueryPort.hasPendingRegistrationFor(used)).isFalse();
    assertThat(tenantQueryPort.hasPendingRegistrationFor(expired)).isFalse();
    assertThat(tenantQueryPort.hasPendingRegistrationFor("nobody-" + shortId() + "@example.com"))
        .isFalse();
    assertThat(tenantQueryPort.hasPendingRegistrationFor(" ")).isFalse();
  }

  private static SelfSignupRequest request(String taxId, String email) {
    return SelfSignupRequest.builder()
        .organizationName("Duplicate Probe " + taxId)
        .taxId(taxId)
        .organizationType(OrganizationType.VERTICAL_MILL)
        .firstName("Harriet")
        .lastName("Lowe")
        .email(email)
        .intent(SignupIntent.PLAYGROUND)
        .acceptedTerms(true)
        .build();
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  private UUID insertTenant() {
    UUID id = UUID.randomUUID();
    String suffix = shortId();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, demo_mode, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, 'REGULAR', 'admin@example.com', 'ACTIVE', '{}', false,"
                + " true, now(), now(), 0)",
            id,
            "DS-" + suffix,
            "ds-" + suffix.toLowerCase(Locale.ROOT),
            "Duplicate Signup " + suffix);
    createdTenants.add(id);
    return id;
  }

  private static void insertOrganisation(UUID tenantId, String taxId) {
    owner()
        .update(
            "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id,"
                + " organization_type, is_active, created_at, updated_at, version) VALUES (?, ?,"
                + " ?, ?, ?, 'WEAVER', true, now(), now(), 0)",
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID().toString(),
            "Existing Weaving " + taxId,
            taxId);
  }

  private static void insertRegistrationToken(
      UUID tenantId, String email, boolean used, String expiresAtSql) {
    owner()
        .update(
            "INSERT INTO common_auth.common_registration_token (id, tenant_id, uid, token,"
                + " contact_value, token_type, expires_at, is_used) VALUES (?, ?, ?, ?, ?,"
                + " 'SELF_SERVICE', "
                + expiresAtSql
                + ", ?)",
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            email,
            used);
  }

  private String insertLoginIdentity() {
    String email = freshEmail("existing");
    owner()
        .update(
            "INSERT INTO common_auth.login_identity (id, email, password_hash) VALUES (?, ?, ?)",
            UUID.randomUUID(),
            email,
            "{noop}not-a-real-hash");
    return email;
  }

  private String freshEmail(String local) {
    String email = (local + "-" + shortId() + "@example.com").toLowerCase(Locale.ROOT);
    createdEmails.add(email);
    return email;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
